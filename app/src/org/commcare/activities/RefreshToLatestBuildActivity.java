package org.commcare.activities;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.preferences.DevSessionRestorer;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.dialogs.AlertDialogFactory;
import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 3/18/16.
 */
public class RefreshToLatestBuildActivity extends Activity {

    private static final String TAG = RefreshToLatestBuildActivity.class.getSimpleName();

    public static final String FROM_LATEST_BUILD_UTIL = "from-test-latest-build-util";

    public static final String KEY_UPDATE_ATTEMPT_RESULT = "result-of-update-attempt";

    // status codes
    public static final String UPDATE_SUCCESS = "update-successful";
    public static final String ALREADY_UP_TO_DATE = "already-up-to-date";
    public static final String UPDATE_ERROR = "update-error";
    public static final String NO_SESSION_ERROR = "no-session-error";

    private int PERFORM_UPDATE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.refresh_latest_build_view);
        ((TextView)findViewById(R.id.status_message))
                .setText(Localization.get("refresh.build.base.message"));

        try {
            DevSessionRestorer.tryAutoLoginPasswordSave(getCurrentUserPassword(), true);
            CommCareApplication._().setPendingRefreshToLatestBuild();
            DevSessionRestorer.saveSessionToPrefs();
            performUpdate();
        } catch (SessionUnavailableException e) {
            showErrorAlertDialog(NO_SESSION_ERROR);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == PERFORM_UPDATE) {
            String status = intent.getStringExtra(KEY_UPDATE_ATTEMPT_RESULT);
            if (UPDATE_SUCCESS.equals(status)) {
                finish();
            } else {
                showErrorAlertDialog(status);
            }
        }
    }

    private void showErrorAlertDialog(String status) {
        String title = "No Refresh Occurred";
        String message;
        if (UPDATE_ERROR.equals(status)) {
            message = Localization.get("refresh.build.update.error");
        } else if (ALREADY_UP_TO_DATE.equals(status)) {
            message = Localization.get("refresh.build.up.to.date");
        } else {
            message = Localization.get("refresh.build.session.error");
        }

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }
        };

        AlertDialogFactory factory = AlertDialogFactory.getBasicAlertFactory(this, title, message, listener);
        factory.showDialog();
    }

    private String getCurrentUserPassword() throws SessionUnavailableException {
        return CommCareApplication._().getSession().getLoggedInUser().getCachedPwd();
    }

    private void performUpdate() {
        Intent i = new Intent(this, UpdateActivity.class);
        i.putExtra(FROM_LATEST_BUILD_UTIL, true);
        startActivityForResult(i, PERFORM_UPDATE);
    }

}
