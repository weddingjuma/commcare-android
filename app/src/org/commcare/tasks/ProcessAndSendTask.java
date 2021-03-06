package org.commcare.tasks;

import android.content.Context;
import android.os.AsyncTask;

import org.commcare.CommCareApplication;
import org.commcare.activities.SyncCapableCommCareActivity;
import org.commcare.logging.AndroidLogger;
import org.commcare.models.FormRecordProcessor;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.suite.model.Profile;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.utils.FormUploadResult;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.commcare.views.notifications.ProcessIssues;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import javax.crypto.spec.SecretKeySpec;

/**
 * @author ctsims
 */
public abstract class ProcessAndSendTask<R> extends CommCareTask<FormRecord, Long, FormUploadResult, R> implements DataSubmissionListener {

    private Context c;
    private String url;
    private FormUploadResult[] results;

    private final int sendTaskId;

    public static final int PROCESSING_PHASE_ID = 8;
    public static final int SEND_PHASE_ID = 9;
    public static final int PROCESSING_PHASE_ID_NO_DIALOG = -8;
    public static final int SEND_PHASE_ID_NO_DIALOG = -9;

    public static final long PROGRESS_ALL_PROCESSED = 8;

    public static final long SUBMISSION_BEGIN = 16;
    public static final long SUBMISSION_START = 32;
    public static final long SUBMISSION_NOTIFY = 64;
    public static final long SUBMISSION_DONE = 128;

    private static final long SUBMISSION_SUCCESS = 1;
    private static final long SUBMISSION_FAIL = 0;

    private FormSubmissionProgressBarListener progressBarListener;
    private List<DataSubmissionListener> formSubmissionListeners;
    private final FormRecordProcessor processor;

    private static final int SUBMISSION_ATTEMPTS = 2;

    private static final Queue<ProcessAndSendTask> processTasks = new LinkedList<>();

    public ProcessAndSendTask(Context c, String url) {
        this(c, url, true);
    }

    /**
     * @param inSyncMode blocks the user with a sync dialog
     */
    public ProcessAndSendTask(Context c, String url, boolean inSyncMode) {
        this.c = c;
        this.url = url;
        this.processor = new FormRecordProcessor(c);
        this.formSubmissionListeners = new ArrayList<>();
        if (inSyncMode) {
            this.sendTaskId = SEND_PHASE_ID;
            this.taskId = PROCESSING_PHASE_ID;
        } else {
            this.sendTaskId = SEND_PHASE_ID_NO_DIALOG;
            this.taskId = PROCESSING_PHASE_ID_NO_DIALOG;
        }
    }

    @Override
    protected FormUploadResult doTaskBackground(FormRecord... records) {
        boolean needToSendLogs = false;

        try {
            results = new FormUploadResult[records.length];
            for (int i = 0; i < records.length; ++i) {
                //Assume failure
                results[i] = FormUploadResult.FAILURE;
            }
            //The first thing we need to do is make sure everything is processed,
            //we can't actually proceed before that.
            try {
                needToSendLogs = checkFormRecordStatus(records);
            } catch (FileNotFoundException e) {
                return FormUploadResult.PROGRESS_SDCARD_REMOVED;
            } catch (TaskCancelledException e) {
                return FormUploadResult.FAILURE;
            }


            this.publishProgress(PROGRESS_ALL_PROCESSED);

            //Put us on the queue!
            synchronized (processTasks) {
                processTasks.add(this);
            }
            boolean needToRefresh;
            try {
                needToRefresh = blockUntilTopOfQueue();
            } catch (TaskCancelledException e) {
                return FormUploadResult.FAILURE;
            }


            if (needToRefresh) {
                //There was another activity before this one. Refresh our models in case
                //they were updated
                for (int i = 0; i < records.length; ++i) {
                    int dbId = records[i].getID();
                    records[i] = processor.getRecord(dbId);
                }
            }

            // Ok, all forms are now processed. Time to focus on sending
            dispatchBeginSubmissionProcessToListeners(records.length);
            sendForms(records);

            return FormUploadResult.getWorstResult(results);
        } catch (SessionUnavailableException sue) {
            this.cancel(false);
            return FormUploadResult.PROGRESS_LOGGED_OUT;
        } finally {
            this.endSubmissionProcess(
                    FormUploadResult.FULL_SUCCESS.equals(FormUploadResult.getWorstResult(results)));

            synchronized (processTasks) {
                processTasks.remove(this);
            }

            if (needToSendLogs) {
                CommCareApplication.instance().notifyLogsPending();
            }
        }
    }

    private boolean checkFormRecordStatus(FormRecord[] records)
            throws FileNotFoundException, TaskCancelledException {
        boolean needToSendLogs = false;
        processor.beginBulkSubmit();
        for (int i = 0; i < records.length; ++i) {
            if (isCancelled()) {
                throw new TaskCancelledException();
            }
            FormRecord record = records[i];

            //If the form is complete, but unprocessed, process it.
            if (FormRecord.STATUS_COMPLETE.equals(record.getStatus())) {
                try {
                    records[i] = processor.process(record);
                } catch (InvalidStructureException e) {
                    CommCareApplication.notificationManager().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.BadTransactions), true);
                    Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record due to transaction data|" + getExceptionText(e));
                    FormRecordCleanupTask.wipeRecord(c, record);
                    needToSendLogs = true;
                } catch (XmlPullParserException e) {
                    CommCareApplication.notificationManager().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.BadTransactions), true);
                    Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record due to bad xml|" + getExceptionText(e));
                    FormRecordCleanupTask.wipeRecord(c, record);
                    needToSendLogs = true;
                } catch (UnfullfilledRequirementsException e) {
                    CommCareApplication.notificationManager().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.BadTransactions), true);
                    Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record due to bad requirements|" + getExceptionText(e));
                    FormRecordCleanupTask.wipeRecord(c, record);
                    needToSendLogs = true;
                } catch (FileNotFoundException e) {
                    if (CommCareApplication.instance().isStorageAvailable()) {
                        //If storage is available generally, this is a bug in the app design
                        Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record because file was missing|" + getExceptionText(e));
                        FormRecordCleanupTask.wipeRecord(c, record);
                    } else {
                        CommCareApplication.notificationManager().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.StorageRemoved), true);
                        //Otherwise, the SD card just got removed, and we need to bail anyway.
                        throw e;
                    }
                } catch (IOException e) {
                    Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "IO Issues processing a form. Tentatively not removing in case they are resolvable|" + getExceptionText(e));
                }
            }
        }
        processor.closeBulkSubmit();
        return needToSendLogs;
    }

    private boolean blockUntilTopOfQueue() throws TaskCancelledException {
        boolean needToRefresh = false;
        while (true) {
            //See if it's our turn to go
            synchronized (processTasks) {
                if (isCancelled()) {
                    processTasks.remove(this);
                    throw new TaskCancelledException();
                }
                //Are we at the head of the queue?
                ProcessAndSendTask head = processTasks.peek();
                if (head == this) {
                    break;
                }
                //Otherwise, is the head of the queue busted?
                //*sigh*. Apparently Cancelled doesn't result in the task status being set
                //to !Running for reasons which baffle me.
                if (head.getStatus() != AsyncTask.Status.RUNNING || head.isCancelled()) {
                    //If so, get rid of it
                    processTasks.poll();
                }
            }
            //If it's not yet quite our turn, take a nap
            try {
                needToRefresh = true;
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return needToRefresh;
    }

    private void sendForms(FormRecord[] records) {
        for (int i = 0; i < records.length; ++i) {
            //See whether we are OK to proceed based on the last form. We're now guaranteeing
            //that forms are sent in order, so we won't proceed unless we succeed. We'll also permit
            //proceeding if there was a local problem with a record, since we'll just move on from that
            //processing.
            if (i > 0 && !(results[i - 1] == FormUploadResult.FULL_SUCCESS || results[i - 1] == FormUploadResult.RECORD_FAILURE)) {
                //Something went wrong with the last form, so we need to cancel this whole shebang
                Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Cancelling submission due to network errors. " + (i - 1) + " forms succesfully sent.");
                break;
            }

            FormRecord record = records[i];
            try {
                //If it's unsent, go ahead and send it
                if (FormRecord.STATUS_UNSENT.equals(record.getStatus())) {
                    File folder;
                    try {
                        folder = new File(record.getPath(c)).getCanonicalFile().getParentFile();
                    } catch (IOException e) {
                        Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "Bizarre. Exception just getting the file reference. Not removing." + getExceptionText(e));
                        continue;
                    }

                    //Good!
                    //Time to Send!
                    try {
                        User mUser = CommCareApplication.instance().getSession().getLoggedInUser();

                        int attemptsMade = 0;
                        while (attemptsMade < SUBMISSION_ATTEMPTS) {
                            if (attemptsMade > 0) {
                                Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Retrying submission. " + (SUBMISSION_ATTEMPTS - attemptsMade) + " attempts remain");
                            }
                            results[i] = FormUploadUtil.sendInstance(i, folder, new SecretKeySpec(record.getAesKey(), "AES"), url, this, mUser);
                            if (results[i] == FormUploadResult.FULL_SUCCESS) {
                                break;
                            } else {
                                attemptsMade++;
                            }
                        }

                        if (results[i] == FormUploadResult.RECORD_FAILURE) {
                            //We tried to submit multiple times and there was a local problem (not a remote problem).
                            //This implies that something is wrong with the current record, and we need to quarantine it.
                            processor.updateRecordStatus(record, FormRecord.STATUS_LIMBO);
                            Logger.log(AndroidLogger.TYPE_ERROR_STORAGE, "Quarantined Form Record");
                            CommCareApplication.notificationManager().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.RecordQuarantined), true);
                        }
                    } catch (FileNotFoundException e) {
                        if (CommCareApplication.instance().isStorageAvailable()) {
                            //If storage is available generally, this is a bug in the app design
                            Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record because file was missing|" + getExceptionText(e));
                            FormRecordCleanupTask.wipeRecord(c, record);
                        } else {
                            //Otherwise, the SD card just got removed, and we need to bail anyway.
                            CommCareApplication.notificationManager().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.StorageRemoved), true);
                            break;
                        }
                        continue;
                    }

                    Profile p = CommCareApplication.instance().getCommCarePlatform().getCurrentProfile();
                    //Check for success
                    if (results[i] == FormUploadResult.FULL_SUCCESS) {
                        //Only delete if this device isn't set up to review.
                        if (p == null || !p.isFeatureActive(Profile.FEATURE_REVIEW)) {
                            FormRecordCleanupTask.wipeRecord(c, record);
                        } else {
                            //Otherwise save and move appropriately
                            processor.updateRecordStatus(record, FormRecord.STATUS_SAVED);
                        }
                    }
                } else {
                    results[i] = FormUploadResult.FULL_SUCCESS;
                }
            } catch (SessionUnavailableException sue) {
                throw sue;
            } catch (Exception e) {
                //Just try to skip for now. Hopefully this doesn't wreck the model :/
                Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Totally Unexpected Error during form submission" + getExceptionText(e));
            }
        }
    }

    public static int pending() {
        synchronized (processTasks) {
            return processTasks.size();
        }
    }

    @Override
    protected void onProgressUpdate(Long... values) {
        if (values.length == 1 && values[0] == PROGRESS_ALL_PROCESSED) {
            this.transitionPhase(sendTaskId);
        }

        super.onProgressUpdate(values);

        if (values.length > 0) {
            if (values[0] == SUBMISSION_BEGIN) {
                dispatchBeginSubmissionProcessToListeners(values[1].intValue());
            } else if (values[0] == SUBMISSION_START) {
                int item = values[1].intValue();
                long size = values[2];
                dispatchStartSubmissionToListeners(item, size);
            } else if (values[0] == SUBMISSION_NOTIFY) {
                int item = values[1].intValue();
                long progress = values[2];
                dispatchNotifyProgressToListeners(item, progress);
            } else if (values[0] == SUBMISSION_DONE) {
                dispatchEndSubmissionProcessToListeners(values[1] == SUBMISSION_SUCCESS);
            }
        }
    }

    public void addProgressBarSubmissionListener(FormSubmissionProgressBarListener listener) {
        this.progressBarListener = listener;
        addSubmissionListener(listener);
    }

    public void addSubmissionListener(DataSubmissionListener submissionListener) {
        formSubmissionListeners.add(submissionListener);
    }

    private void dispatchBeginSubmissionProcessToListeners(int totalItems) {
        for (DataSubmissionListener listener : formSubmissionListeners) {
            listener.beginSubmissionProcess(totalItems);
        }
    }

    private void dispatchStartSubmissionToListeners(int itemNumber, long length) {
        for (DataSubmissionListener listener : formSubmissionListeners) {
            listener.startSubmission(itemNumber, length);
        }
    }

    private void dispatchNotifyProgressToListeners(int itemNumber, long progress) {
        for (DataSubmissionListener listener : formSubmissionListeners) {
            listener.notifyProgress(itemNumber, progress);
        }
    }

    private void dispatchEndSubmissionProcessToListeners(boolean success) {
        for (DataSubmissionListener listener : formSubmissionListeners) {
            listener.endSubmissionProcess(success);
        }
    }

    @Override
    protected void onPostExecute(FormUploadResult result) {
        super.onPostExecute(result);

        clearState();
    }

    private void clearState() {
        c = null;
        url = null;
        results = null;
    }

    protected int getSuccessfulSends() {
        int successes = 0;
        for (FormUploadResult formResult : results) {
            if (formResult != null && FormUploadResult.FULL_SUCCESS == formResult) {
                successes++;
            }
        }
        return successes;
    }

    //Wrappers for the internal stuff
    @Override
    public void beginSubmissionProcess(int totalItems) {
        this.publishProgress(SUBMISSION_BEGIN, (long)totalItems);
    }

    @Override
    public void startSubmission(int itemNumber, long sizeOfItem) {
        this.publishProgress(SUBMISSION_START, (long)itemNumber, sizeOfItem);
    }

    @Override
    public void notifyProgress(int itemNumber, long progress) {
        this.publishProgress(SUBMISSION_NOTIFY, (long)itemNumber, progress);
    }

    @Override
    public void endSubmissionProcess(boolean success) {
        if (success) {
            this.publishProgress(SUBMISSION_DONE, SUBMISSION_SUCCESS);
        } else {
            this.publishProgress(SUBMISSION_DONE, SUBMISSION_FAIL);
        }
    }

    private String getExceptionText(Exception e) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(bos));
            return new String(bos.toByteArray());
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();

        dispatchEndSubmissionProcessToListeners(false);
        CommCareApplication.notificationManager().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.LoggedOut));

        clearState();
    }

    @Override
    public void connect(CommCareTaskConnector<R> connector) {
        super.connect(connector);
        if (progressBarListener != null) {
            progressBarListener.attachToNewActivity(
                    (SyncCapableCommCareActivity)connector.getReceiver());
        }
    }

    private static class TaskCancelledException extends Exception {
    }
}
