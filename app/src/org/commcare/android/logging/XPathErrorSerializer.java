package org.commcare.android.logging;

import org.commcare.android.database.SqlStorage;
import org.javarosa.core.log.LogEntry;
import org.javarosa.core.log.StreamLogSerializer;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.util.SortedIntSet;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.Hashtable;

/**
 * Convert xpath error logs to xml
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class XPathErrorSerializer
        extends StreamLogSerializer
        implements DeviceReportElement {
    private final SqlStorage<XPathErrorEntry> errorLogStorage;
    private XmlSerializer serializer;

    /**
     * Report format version for ability to dispatch different parser on server
     */
    private static final int ERROR_FORMAT_VERSION = 1;

    public XPathErrorSerializer(final SqlStorage<XPathErrorEntry> logStorage) {
        errorLogStorage = logStorage;
        this.setPurger(new Purger() {
            @Override
            public void purge(final SortedIntSet IDs) {
                logStorage.removeAll(new EntityFilter<LogEntry>() {
                    public int preFilter(int id, Hashtable<String, Object> metaData) {
                        return IDs.contains(id) ? PREFILTER_INCLUDE : PREFILTER_EXCLUDE;
                    }

                    public boolean matches(LogEntry e) {
                        throw new RuntimeException("can't happen");
                    }
                });
            }
        });
    }

    @Override
    public void writeToDeviceReport(XmlSerializer serializer) throws IOException {
        this.serializer = serializer;

        serializer.startTag(DeviceReportWriter.XMLNS, "user_error_subreport");
        serializer.attribute(null, "version", ERROR_FORMAT_VERSION + "");

        try {
            for (XPathErrorEntry entry : errorLogStorage) {
                serializeLog(entry.getID(), entry);
            }
        } finally {
            serializer.endTag(DeviceReportWriter.XMLNS, "user_error_subreport");
        }
    }

    @Override
    protected void serializeLog(LogEntry entry) throws IOException {
        final XPathErrorEntry errorEntry = (XPathErrorEntry)entry;
        String dateString =
                DateUtils.formatDateTime(errorEntry.getTime(), DateUtils.FORMAT_ISO8601);

        serializer.startTag(DeviceReportWriter.XMLNS, "user_error");
        try {
            serializer.attribute(null, "date", dateString);
            writeText("type", errorEntry.getType());
            writeText("msg", errorEntry.getMessage());
            writeText("user_id", errorEntry.getUserId());
            writeText("session", errorEntry.getSessionPath());
            writeText("version", errorEntry.getAppVersion() + "");
            writeText("app_id", errorEntry.getAppId());
            writeText("expr", errorEntry.getExpression());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            serializer.endTag(DeviceReportWriter.XMLNS, "user_error");
        }
    }

    private void writeText(String element, String text)
            throws IllegalArgumentException, IllegalStateException, IOException {
        serializer.startTag(DeviceReportWriter.XMLNS, element);
        try {
            serializer.text(text);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            serializer.endTag(DeviceReportWriter.XMLNS, element);
        }
    }
}
