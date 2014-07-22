
package com.example.test;

import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import android.provider.CalendarContract.Reminders;

public class MainActivity extends ActionBarActivity {
    private static final String CTS_TEST_TYPE = "LOCAL";
    // @formatter:off
    private static final String[] TIME_ZONES = new String[] {
            "UTC",
            "America/Los_Angeles",
            "Asia/Beirut",
            "Pacific/Auckland", };
    private static final String SQL_WHERE_ID = Events._ID + "=?";
    ContentResolver mContentResolver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContentResolver = getContentResolver();
        //testFullRecurrenceUpdate();
        testForwardRecurrenceExceptions();

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }


    }
    static String generateCalendarOwnerEmail(String account) {
        return "OWNER_" + account + "@example.com";
    }


    public static int deleteCalendarByAccount(ContentResolver resolver, String account) {
        return resolver.delete(Calendars.CONTENT_URI, Calendars.ACCOUNT_NAME + "=?",
                new String[] { account });
    }
    static Uri asSyncAdapter(Uri uri, String account, String accountType) {
        return uri.buildUpon()
                .appendQueryParameter(android.provider.CalendarContract.CALLER_IS_SYNCADAPTER,
                        "true")
                .appendQueryParameter(Calendars.ACCOUNT_NAME, account)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, accountType).build();
    }
    public static ContentValues getNewCalendarValues(
            String account, int seed) {
        String seedString = Long.toString(seed);
        ContentValues values = new ContentValues();
        values.put(Calendars.ACCOUNT_TYPE, CTS_TEST_TYPE);

        values.put(Calendars.ACCOUNT_NAME, account);
        values.put(Calendars._SYNC_ID, "SYNC_ID:" + seedString);
        values.put(Calendars.CAL_SYNC7, "SYNC_V:" + seedString);
        values.put(Calendars.CAL_SYNC8, "SYNC_TIME:" + seedString);
        values.put(Calendars.DIRTY, 0);
        values.put(Calendars.OWNER_ACCOUNT, generateCalendarOwnerEmail(account));

        values.put(Calendars.NAME, seedString);
        values.put(Calendars.CALENDAR_DISPLAY_NAME, "DISPLAY_" + seedString);

        values.put(Calendars.CALENDAR_ACCESS_LEVEL, (seed % 8) * 100);

        values.put(Calendars.CALENDAR_COLOR, 0xff000000 + seed);
        values.put(Calendars.VISIBLE, seed % 2);
        values.put(Calendars.SYNC_EVENTS, 1);   // must be 1 for recurrence expansion
        values.put(Calendars.CALENDAR_LOCATION, "LOCATION:" + seedString);
        values.put(Calendars.CALENDAR_TIME_ZONE, TIME_ZONES[seed % TIME_ZONES.length]);
        values.put(Calendars.CAN_ORGANIZER_RESPOND, seed % 2);
        values.put(Calendars.CAN_MODIFY_TIME_ZONE, seed % 2);
        values.put(Calendars.MAX_REMINDERS, 3);
        values.put(Calendars.ALLOWED_REMINDERS, "0,1,2");   // does not include SMS (3)
        values.put(Calendars.ALLOWED_ATTENDEE_TYPES, "0,1,2,3");
        values.put(Calendars.ALLOWED_AVAILABILITY, "0,1,2,3");
        values.put(Calendars.CAL_SYNC1, "SYNC1:" + seedString);
        values.put(Calendars.CAL_SYNC2, "SYNC2:" + seedString);
        values.put(Calendars.CAL_SYNC3, "SYNC3:" + seedString);
        values.put(Calendars.CAL_SYNC4, "SYNC4:" + seedString);
        values.put(Calendars.CAL_SYNC5, "SYNC5:" + seedString);
        values.put(Calendars.CAL_SYNC6, "SYNC6:" + seedString);

        return values;
    }


    private long createAndVerifyCalendar(String account, int seed, ContentValues values) {
        // Create a calendar
        if (values == null) {
            values = getNewCalendarValues(account, seed);
        }
        Uri syncUri = asSyncAdapter(Calendars.CONTENT_URI, account, CTS_TEST_TYPE);
        Uri uri = mContentResolver.insert(syncUri, values);
        long calendarId = ContentUris.parseId(uri);
        //assertTrue(calendarId >= 0);

        //verifyCalendar(account, values, calendarId, 1);
        return calendarId;
    }
    private long createAndVerifyEvent(String account, int seed, long calendarId,
            boolean asSyncAdapter, ContentValues values) {
        // Create an event
        if (values == null) {
            values = EventHelper.getNewEventValues(account, seed, calendarId, asSyncAdapter);
        }
        Uri insertUri = Events.CONTENT_URI;
        if (asSyncAdapter) {
            insertUri = asSyncAdapter(insertUri, account, CTS_TEST_TYPE);
        }
        Uri uri = mContentResolver.insert(insertUri, values);
        //assertNotNull(uri);

        // Verify
        EventHelper.addDefaultReadOnlyValues(values, account, asSyncAdapter);
        long eventId = ContentUris.parseId(uri);
        //assertTrue(eventId >= 0);

        //verifyEvent(values, eventId);
        return eventId;
    }
    private Cursor getInstances(String timeZone, String startWhen, String endWhen,
            String[] projection, long[] calendarIds) {
        Time startTime = new Time(timeZone);
        startTime.parse3339(startWhen);
        long startMillis = startTime.toMillis(false);

        Time endTime = new Time(timeZone);
        endTime.parse3339(endWhen);
        long endMillis = endTime.toMillis(false);

        // We want a list of instances that occur between the specified dates.  Use the
        // "instances/when" URI.
        Uri uri = Uri.withAppendedPath(CalendarContract.Instances.CONTENT_URI,
                startMillis + "/" + endMillis);

        String where = null;
        for (int i = 0; i < calendarIds.length; i++) {
            if (i > 0) {
                where += " OR ";
            } else {
                where = "";
            }
            where += (Instances.CALENDAR_ID + "=" + calendarIds[i]);
        }
        Cursor instances = mContentResolver.query(uri, projection, where, null,
                projection[0] + " ASC");

        return instances;
    }
    public void testFullRecurrenceUpdate() {
        String account = "ref_account";
        int seed = 0;

        // Clean up just in case
        deleteCalendarByAccount(mContentResolver, account);

        // Create calendar
        long calendarId = createAndVerifyCalendar(account, seed++, null);

        // Create recurring event
        String rrule = "FREQ=DAILY;WKST=MO;COUNT=100";
        ContentValues eventValues = EventHelper.getNewRecurringEventValues(account, seed++,
                calendarId, true, "1997-08-29T02:14:00", "PT1H", rrule);
        long eventId = createAndVerifyEvent(account, seed++, calendarId, true, eventValues);
        //Log.i(TAG, "+++ eventId is " + eventId);

        // Get some instances.
        String timeZone = eventValues.getAsString(Events.EVENT_TIMEZONE);
        String testStart = "1997-08-01T00:00:00";
        String testEnd = "1997-08-31T23:59:59";
        String[] projection = { Instances.BEGIN, Instances.EVENT_LOCATION };
        String newLocation = "NEW!";

        Cursor instances = getInstances(timeZone, testStart, testEnd, projection,
                new long[] { calendarId });
        //if (DEBUG_RECURRENCE) {
        //    dumpInstances(instances, timeZone, "initial");
        //}

        //assertEquals("initial recurrence instance count", 3, instances.getCount());
        Log.e("tag", "instances.getCount()="+instances.getCount());

        instances.moveToFirst();
        long startMillis = instances.getLong(0);
        ContentValues excepValues = EventHelper.getNewExceptionValues(startMillis);
        excepValues.put(Events.RRULE, rrule);   // identifies this as an "all future events" excep
        excepValues.put(Events.EVENT_LOCATION, newLocation);
        long excepEventId = createAndVerifyException(account, eventId, excepValues, true);
        instances.close();

        // Check results.
        //assertEquals("full update does not create new ID", eventId, excepEventId);

        instances = getInstances(timeZone, testStart, testEnd, projection,
                new long[] { calendarId });
        Log.e("tag", "instances.getCount()="+instances.getCount());
        //assertEquals("post-update instance count", 3, instances.getCount());
        //while (instances.moveToNext()) {
        //    assertEquals("new location", newLocation, instances.getString(1));
        //}
        instances.close();

        // delete the calendar
        //removeAndVerifyCalendar(account, calendarId);
    }
    public void testForwardRecurrenceExceptions() {
        String account = "refx_account";
        int seed = 0;

        // Clean up just in case
        deleteCalendarByAccount(mContentResolver, account);

        // Create calendar
        long calendarId = createAndVerifyCalendar(account, seed++, null);

        // Create recurring event
        ContentValues eventValues = EventHelper.getNewRecurringEventValues(account, seed++,
                calendarId, true, "1999-01-01T06:00:00", "PT1H", "FREQ=WEEKLY;WKST=SU;COUNT=10");
        long eventId = createAndVerifyEvent(account, seed++, calendarId, true, eventValues);

        // Add some attendees and reminders.
        addAttendees(account, eventId, seed++);
        addReminders(account, eventId, seed++);

        // Get some instances.
        String timeZone = eventValues.getAsString(Events.EVENT_TIMEZONE);
        String testStart = "1999-01-01T00:00:00";
        String testEnd = "1999-01-29T23:59:59";
        String[] projection = { Instances.BEGIN, Instances.START_MINUTE };

        Cursor instances = getInstances(timeZone, testStart, testEnd, projection,
                new long[] { calendarId });
        //if (DEBUG_RECURRENCE) {
        //    dumpInstances(instances, timeZone, "initial");
        //}

        //assertEquals("initial recurrence instance count", 5, instances.getCount());
        Log.e("tag", "instances.getCount()="+instances.getCount());

        // Modify starting from 3rd instance.
        instances.moveToPosition(2);

        long startMillis;
        ContentValues excepValues;

        // Replace with a new recurrence rule.  We move the start time an hour later, and cap
        // it at two instances.
        startMillis = instances.getLong(0);
        excepValues = EventHelper.getNewExceptionValues(startMillis);
        excepValues.put(Events.DTSTART, startMillis + 3600*1000);
        excepValues.put(Events.RRULE, "FREQ=WEEKLY;COUNT=2;WKST=SU");
        long excepEventId = createAndVerifyException(account, eventId, excepValues, true);
        instances.close();


        // Check to see if it took.
        instances = getInstances(timeZone, testStart, testEnd, projection,
                new long[] { calendarId });
        //if (DEBUG_RECURRENCE) {
        //    dumpInstances(instances, timeZone, "with new rule");
        //}

        //assertEquals("count with exception", 4, instances.getCount());
        Log.e("tag", "instances.getCount()="+instances.getCount());

        /*long prevMinute = -1;
        for (int i = 0; i < 4; i++) {
            long startMinute;
            instances.moveToNext();
            switch (i) {
                case 0:
                    startMinute = instances.getLong(1);
                    break;
                case 1:
                case 3:
                    startMinute = instances.getLong(1);
                    assertEquals("first/last pairs match", prevMinute, startMinute);
                    break;
                case 2:
                    startMinute = instances.getLong(1);
                    assertFalse("first two != last two", prevMinute == startMinute);
                    break;
                default:
                    fail();
                    startMinute = -1;   // make compiler happy
                    break;
            }

            prevMinute = startMinute;
        }*/
        instances.close();

        // delete the calendar
        //removeAndVerifyCalendar(account, calendarId);
    }
    private void addReminders(String account, long eventId, int seed) {
        addReminder(mContentResolver, eventId, seed * 5, Reminders.METHOD_ALERT);
    }
    public static long addReminder(ContentResolver resolver, long eventId, int minutes,
            int method) {
        Uri uri = Reminders.CONTENT_URI;

        ContentValues reminder = new ContentValues();
        reminder.put(Reminders.EVENT_ID, eventId);
        reminder.put(Reminders.MINUTES, minutes);
        reminder.put(Reminders.METHOD, method);
        Uri result = resolver.insert(uri, reminder);
        return ContentUris.parseId(result);
    }

    private void addAttendees(String account, long eventId, int seed) {
        //assertTrue(eventId >= 0);
        addAttendee(mContentResolver, eventId,
                "Attender" + seed,
                generateCalendarOwnerEmail(account),
                Attendees.ATTENDEE_STATUS_ACCEPTED,
                Attendees.RELATIONSHIP_ORGANIZER,
                Attendees.TYPE_NONE);
        seed++;

        addAttendee(mContentResolver, eventId,
                "Attender" + seed,
                "attender" + seed + "@example.com",
                Attendees.ATTENDEE_STATUS_TENTATIVE,
                Attendees.RELATIONSHIP_NONE,
                Attendees.TYPE_NONE);
    }

    public static long addAttendee(ContentResolver resolver, long eventId, String name,
            String email, int status, int relationship, int type) {
        Uri uri = Attendees.CONTENT_URI;

        ContentValues attendee = new ContentValues();
        attendee.put(Attendees.EVENT_ID, eventId);
        attendee.put(Attendees.ATTENDEE_NAME, name);
        attendee.put(Attendees.ATTENDEE_EMAIL, email);
        attendee.put(Attendees.ATTENDEE_STATUS, status);
        attendee.put(Attendees.ATTENDEE_RELATIONSHIP, relationship);
        attendee.put(Attendees.ATTENDEE_TYPE, type);
        Uri result = resolver.insert(uri, attendee);
        return ContentUris.parseId(result);
    }

    private long createAndVerifyException(String account, long originalEventId,
            ContentValues values, boolean asSyncAdapter) {
        // Create the exception
        Uri uri = Uri.withAppendedPath(Events.CONTENT_EXCEPTION_URI,
                String.valueOf(originalEventId));
        if (asSyncAdapter) {
            uri = asSyncAdapter(uri, account, CTS_TEST_TYPE);
        }
        Uri resultUri = mContentResolver.insert(uri, values);
        //assertNotNull(resultUri);
        long eventId = ContentUris.parseId(resultUri);
        //assertTrue(eventId >= 0);
        return eventId;
    }
    public static ContentValues getNewExceptionValues(long instanceStartMillis) {
        ContentValues values = new ContentValues();
        values.put(Events.ORIGINAL_INSTANCE_TIME, instanceStartMillis);

        return values;
    }

    public static ContentValues getNewEventValues(
            String account, int seed, long calendarId, boolean asSyncAdapter) {
        String seedString = Long.toString(seed);
        ContentValues values = new ContentValues();
        values.put(Events.ORGANIZER, "ORGANIZER:" + seedString);

        values.put(Events.TITLE, "TITLE:" + seedString);
        values.put(Events.EVENT_LOCATION, "LOCATION_" + seedString);

        values.put(Events.CALENDAR_ID, calendarId);

        values.put(Events.DESCRIPTION, "DESCRIPTION:" + seedString);
        values.put(Events.STATUS, seed % 2);    // avoid STATUS_CANCELED for general testing

        values.put(Events.DTSTART, seed);
        values.put(Events.DTEND, seed + DateUtils.HOUR_IN_MILLIS);
        values.put(Events.EVENT_TIMEZONE, TIME_ZONES[seed % TIME_ZONES.length]);
        values.put(Events.EVENT_COLOR, seed);
        // values.put(Events.EVENT_TIMEZONE2, TIME_ZONES[(seed +1) %
        // TIME_ZONES.length]);
        if ((seed % 2) == 0) {
            // Either set to zero, or leave unset to get default zero.
            // Must be 0 or dtstart/dtend will get adjusted.
            values.put(Events.ALL_DAY, 0);
        }
        values.put(Events.ACCESS_LEVEL, seed % 4);
        values.put(Events.AVAILABILITY, seed % 2);
        values.put(Events.HAS_EXTENDED_PROPERTIES, seed % 2);
        values.put(Events.HAS_ATTENDEE_DATA, seed % 2);
        values.put(Events.GUESTS_CAN_MODIFY, seed % 2);
        values.put(Events.GUESTS_CAN_INVITE_OTHERS, seed % 2);
        values.put(Events.GUESTS_CAN_SEE_GUESTS, seed % 2);

        // Default is STATUS_TENTATIVE (0).  We either set it to that explicitly, or leave
        // it set to the default.
        if (seed != Events.STATUS_TENTATIVE) {
            values.put(Events.SELF_ATTENDEE_STATUS, Events.STATUS_TENTATIVE);
        }

        if (asSyncAdapter) {
            values.put(Events._SYNC_ID, "SYNC_ID:" + seedString);
            values.put(Events.SYNC_DATA4, "SYNC_V:" + seedString);
            values.put(Events.SYNC_DATA5, "SYNC_TIME:" + seedString);
            values.put(Events.SYNC_DATA3, "HTML:" + seedString);
            values.put(Events.SYNC_DATA6, "COMMENTS:" + seedString);
            values.put(Events.DIRTY, 0);
            values.put(Events.SYNC_DATA8, "0");
        } else {
            // only the sync adapter can set the DIRTY flag
            //values.put(Events.DIRTY, 1);
        }
        // values.put(Events.SYNC1, "SYNC1:" + seedString);
        // values.put(Events.SYNC2, "SYNC2:" + seedString);
        // values.put(Events.SYNC3, "SYNC3:" + seedString);
        // values.put(Events.SYNC4, "SYNC4:" + seedString);
        // values.put(Events.SYNC5, "SYNC5:" + seedString);
//        Events.RRULE,
//        Events.RDATE,
//        Events.EXRULE,
//        Events.EXDATE,
//        // Events.ORIGINAL_ID
//        Events.ORIGINAL_EVENT, // rename ORIGINAL_SYNC_ID
//        Events.ORIGINAL_INSTANCE_TIME,
//        Events.ORIGINAL_ALL_DAY,

        return values;
    }
    public static ContentValues getNewRecurringEventValues(String account, int seed,
            long calendarId, boolean asSyncAdapter, String startWhen, String duration,
            String rrule) {

        // Set up some general stuff.
        ContentValues values = getNewEventValues(account, seed, calendarId, asSyncAdapter);

        // Replace the DTSTART field.
        String timeZone = values.getAsString(Events.EVENT_TIMEZONE);
        Time time = new Time(timeZone);
        time.parse3339(startWhen);
        values.put(Events.DTSTART, time.toMillis(false));

        // Add in the recurrence-specific fields, and drop DTEND.
        values.put(Events.RRULE, rrule);
        values.put(Events.DURATION, duration);
        values.remove(Events.DTEND);

        return values;
    }

    private static class EventHelper {
        public static final String[] EVENTS_PROJECTION = new String[] {
            Events._ID,
            Events.ACCOUNT_NAME,
            Events.ACCOUNT_TYPE,
            Events.OWNER_ACCOUNT,
            // Events.ORGANIZER_CAN_RESPOND, from Calendars
            // Events.CAN_CHANGE_TZ, from Calendars
            // Events.MAX_REMINDERS, from Calendars
            Events.CALENDAR_ID,
            // Events.CALENDAR_DISPLAY_NAME, from Calendars
            // Events.CALENDAR_COLOR, from Calendars
            // Events.CALENDAR_ACL, from Calendars
            // Events.CALENDAR_VISIBLE, from Calendars
            Events.SYNC_DATA3,
            Events.SYNC_DATA6,
            Events.TITLE,
            Events.EVENT_LOCATION,
            Events.DESCRIPTION,
            Events.STATUS,
            Events.SELF_ATTENDEE_STATUS,
            Events.DTSTART,
            Events.DTEND,
            Events.EVENT_TIMEZONE,
            Events.EVENT_END_TIMEZONE,
            Events.EVENT_COLOR,
            Events.EVENT_COLOR_KEY,
            Events.DURATION,
            Events.ALL_DAY,
            Events.ACCESS_LEVEL,
            Events.AVAILABILITY,
            Events.HAS_ALARM,
            Events.HAS_EXTENDED_PROPERTIES,
            Events.RRULE,
            Events.RDATE,
            Events.EXRULE,
            Events.EXDATE,
            Events.ORIGINAL_ID,
            Events.ORIGINAL_SYNC_ID,
            Events.ORIGINAL_INSTANCE_TIME,
            Events.ORIGINAL_ALL_DAY,
            Events.LAST_DATE,
            Events.HAS_ATTENDEE_DATA,
            Events.GUESTS_CAN_MODIFY,
            Events.GUESTS_CAN_INVITE_OTHERS,
            Events.GUESTS_CAN_SEE_GUESTS,
            Events.ORGANIZER,
            Events.DELETED,
            Events._SYNC_ID,
            Events.SYNC_DATA4,
            Events.SYNC_DATA5,
            Events.DIRTY,
            Events.SYNC_DATA8,
            Events.SYNC_DATA2,
            Events.SYNC_DATA1,
            Events.SYNC_DATA2,
            Events.SYNC_DATA3,
            Events.SYNC_DATA4,
        };
        // @formatter:on

        private EventHelper() {}    // do not instantiate this class

        /**
         * Constructs a set of name/value pairs that can be used to create a Calendar event.
         * Various fields are generated from the seed value.
         */
        public static ContentValues getNewEventValues(
                String account, int seed, long calendarId, boolean asSyncAdapter) {
            String seedString = Long.toString(seed);
            ContentValues values = new ContentValues();
            values.put(Events.ORGANIZER, "ORGANIZER:" + seedString);

            values.put(Events.TITLE, "TITLE:" + seedString);
            values.put(Events.EVENT_LOCATION, "LOCATION_" + seedString);

            values.put(Events.CALENDAR_ID, calendarId);

            values.put(Events.DESCRIPTION, "DESCRIPTION:" + seedString);
            values.put(Events.STATUS, seed % 2);    // avoid STATUS_CANCELED for general testing

            values.put(Events.DTSTART, seed);
            values.put(Events.DTEND, seed + DateUtils.HOUR_IN_MILLIS);
            values.put(Events.EVENT_TIMEZONE, TIME_ZONES[seed % TIME_ZONES.length]);
            values.put(Events.EVENT_COLOR, seed);
            // values.put(Events.EVENT_TIMEZONE2, TIME_ZONES[(seed +1) %
            // TIME_ZONES.length]);
            if ((seed % 2) == 0) {
                // Either set to zero, or leave unset to get default zero.
                // Must be 0 or dtstart/dtend will get adjusted.
                values.put(Events.ALL_DAY, 0);
            }
            values.put(Events.ACCESS_LEVEL, seed % 4);
            values.put(Events.AVAILABILITY, seed % 2);
            values.put(Events.HAS_EXTENDED_PROPERTIES, seed % 2);
            values.put(Events.HAS_ATTENDEE_DATA, seed % 2);
            values.put(Events.GUESTS_CAN_MODIFY, seed % 2);
            values.put(Events.GUESTS_CAN_INVITE_OTHERS, seed % 2);
            values.put(Events.GUESTS_CAN_SEE_GUESTS, seed % 2);

            // Default is STATUS_TENTATIVE (0).  We either set it to that explicitly, or leave
            // it set to the default.
            if (seed != Events.STATUS_TENTATIVE) {
                values.put(Events.SELF_ATTENDEE_STATUS, Events.STATUS_TENTATIVE);
            }

            if (asSyncAdapter) {
                values.put(Events._SYNC_ID, "SYNC_ID:" + seedString);
                values.put(Events.SYNC_DATA4, "SYNC_V:" + seedString);
                values.put(Events.SYNC_DATA5, "SYNC_TIME:" + seedString);
                values.put(Events.SYNC_DATA3, "HTML:" + seedString);
                values.put(Events.SYNC_DATA6, "COMMENTS:" + seedString);
                values.put(Events.DIRTY, 0);
                values.put(Events.SYNC_DATA8, "0");
            } else {
                // only the sync adapter can set the DIRTY flag
                //values.put(Events.DIRTY, 1);
            }
            // values.put(Events.SYNC1, "SYNC1:" + seedString);
            // values.put(Events.SYNC2, "SYNC2:" + seedString);
            // values.put(Events.SYNC3, "SYNC3:" + seedString);
            // values.put(Events.SYNC4, "SYNC4:" + seedString);
            // values.put(Events.SYNC5, "SYNC5:" + seedString);
//            Events.RRULE,
//            Events.RDATE,
//            Events.EXRULE,
//            Events.EXDATE,
//            // Events.ORIGINAL_ID
//            Events.ORIGINAL_EVENT, // rename ORIGINAL_SYNC_ID
//            Events.ORIGINAL_INSTANCE_TIME,
//            Events.ORIGINAL_ALL_DAY,

            return values;
        }

        /**
         * Constructs a set of name/value pairs that can be used to create a recurring
         * Calendar event.
         *
         * A duration of "P1D" is treated as an all-day event.
         *
         * @param startWhen Starting date/time in RFC 3339 format
         * @param duration Event duration, in RFC 2445 duration format
         * @param rrule Recurrence rule
         * @return name/value pairs to use when creating event
         */
        public static ContentValues getNewRecurringEventValues(String account, int seed,
                long calendarId, boolean asSyncAdapter, String startWhen, String duration,
                String rrule) {

            // Set up some general stuff.
            ContentValues values = getNewEventValues(account, seed, calendarId, asSyncAdapter);

            // Replace the DTSTART field.
            String timeZone = values.getAsString(Events.EVENT_TIMEZONE);
            Time time = new Time(timeZone);
            time.parse3339(startWhen);
            values.put(Events.DTSTART, time.toMillis(false));

            // Add in the recurrence-specific fields, and drop DTEND.
            values.put(Events.RRULE, rrule);
            values.put(Events.DURATION, duration);
            values.remove(Events.DTEND);

            return values;
        }

        /**
         * Constructs the basic name/value pairs required for an exception to a recurring event.
         *
         * @param instanceStartMillis The start time of the instance
         * @return name/value pairs to use when creating event
         */
        public static ContentValues getNewExceptionValues(long instanceStartMillis) {
            ContentValues values = new ContentValues();
            values.put(Events.ORIGINAL_INSTANCE_TIME, instanceStartMillis);

            return values;
        }

        public static ContentValues getUpdateEventValuesWithOriginal(ContentValues original,
                int seed, boolean asSyncAdapter) {
            String seedString = Long.toString(seed);
            ContentValues values = new ContentValues();

            values.put(Events.TITLE, "TITLE:" + seedString);
            values.put(Events.EVENT_LOCATION, "LOCATION_" + seedString);
            values.put(Events.DESCRIPTION, "DESCRIPTION:" + seedString);
            values.put(Events.STATUS, seed % 3);

            values.put(Events.DTSTART, seed);
            values.put(Events.DTEND, seed + DateUtils.HOUR_IN_MILLIS);
            values.put(Events.EVENT_TIMEZONE, TIME_ZONES[seed % TIME_ZONES.length]);
            // values.put(Events.EVENT_TIMEZONE2, TIME_ZONES[(seed +1) %
            // TIME_ZONES.length]);
            values.put(Events.ACCESS_LEVEL, seed % 4);
            values.put(Events.AVAILABILITY, seed % 2);
            values.put(Events.HAS_EXTENDED_PROPERTIES, seed % 2);
            values.put(Events.HAS_ATTENDEE_DATA, seed % 2);
            values.put(Events.GUESTS_CAN_MODIFY, seed % 2);
            values.put(Events.GUESTS_CAN_INVITE_OTHERS, seed % 2);
            values.put(Events.GUESTS_CAN_SEE_GUESTS, seed % 2);
            if (asSyncAdapter) {
                values.put(Events._SYNC_ID, "SYNC_ID:" + seedString);
                values.put(Events.SYNC_DATA4, "SYNC_V:" + seedString);
                values.put(Events.SYNC_DATA5, "SYNC_TIME:" + seedString);
                values.put(Events.DIRTY, 0);
            }
            original.putAll(values);
            return values;
        }

        public static void addDefaultReadOnlyValues(ContentValues values, String account,
                boolean asSyncAdapter) {
            values.put(Events.SELF_ATTENDEE_STATUS, Events.STATUS_TENTATIVE);
            values.put(Events.DELETED, 0);
            values.put(Events.DIRTY, asSyncAdapter ? 0 : 1);
            values.put(Events.OWNER_ACCOUNT, generateCalendarOwnerEmail(account));
            values.put(Events.ACCOUNT_TYPE, CTS_TEST_TYPE);
            values.put(Events.ACCOUNT_NAME, account);
        }

        /**
         * Generates a RFC2445-format duration string.
         */
        private static String generateDurationString(long durationMillis, boolean isAllDay) {
            long durationSeconds = durationMillis / 1000;

            // The server may react differently to an all-day event specified as "P1D" than
            // it will to "PT86400S"; see b/1594638.
            if (isAllDay && (durationSeconds % 86400) == 0) {
                return "P" + durationSeconds / 86400 + "D";
            } else {
                return "PT" + durationSeconds + "S";
            }
        }

        /**
         * Deletes the event, and updates the values.
         * @param resolver The resolver to issue the query against.
         * @param uri The deletion URI.
         * @param values Set of values to update (sets DELETED and DIRTY).
         * @return The number of rows modified.
         */
        public static int deleteEvent(ContentResolver resolver, Uri uri, ContentValues values) {
            values.put(Events.DELETED, 1);
            values.put(Events.DIRTY, 1);
            return resolver.delete(uri, null, null);
        }

        public static int deleteEventAsSyncAdapter(ContentResolver resolver, Uri uri,
                String account) {
            Uri syncUri = asSyncAdapter(uri, account, CTS_TEST_TYPE);
            return resolver.delete(syncUri, null, null);
        }

        public static Cursor getEventsByAccount(ContentResolver resolver, String account) {
            String selection = Calendars.ACCOUNT_TYPE + "=?";
            String[] selectionArgs;
            if (account != null) {
                selection += " AND " + Calendars.ACCOUNT_NAME + "=?";
                selectionArgs = new String[2];
                selectionArgs[1] = account;
            } else {
                selectionArgs = new String[1];
            }
            selectionArgs[0] = CTS_TEST_TYPE;
            return resolver.query(Events.CONTENT_URI, EVENTS_PROJECTION, selection, selectionArgs,
                    null);
        }

        public static Cursor getEventByUri(ContentResolver resolver, Uri uri) {
            return resolver.query(uri, EVENTS_PROJECTION, null, null, null);
        }

        /**
         * Looks up the specified Event in the database and returns the "selfAttendeeStatus"
         * value.
         */
        public static int lookupSelfAttendeeStatus(ContentResolver resolver, long eventId) {
            return getIntFromDatabase(resolver, Events.CONTENT_URI, eventId,
                    Events.SELF_ATTENDEE_STATUS);
        }

        private static int getIntFromDatabase(ContentResolver resolver, Uri uri, long rowId,
                String columnName) {
            String[] projection = { columnName };
            String selection = SQL_WHERE_ID;
            String[] selectionArgs = { String.valueOf(rowId) };

            Cursor c = resolver.query(uri, projection, selection, selectionArgs, null);
            try {
                //assertEquals(1, c.getCount());
                c.moveToFirst();
                return c.getInt(0);
            } finally {
                c.close();
            }
        }
        /**
         * Looks up the specified Event in the database and returns the "hasAlarm"
         * value.
         */
        public static int lookupHasAlarm(ContentResolver resolver, long eventId) {
            return getIntFromDatabase(resolver, Events.CONTENT_URI, eventId,
                    Events.HAS_ALARM);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }
    }

}
