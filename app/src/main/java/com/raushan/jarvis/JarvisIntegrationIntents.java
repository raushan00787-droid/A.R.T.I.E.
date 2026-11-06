package com.raushan.jarvis;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CalendarContract;

final class JarvisIntegrationIntents {
    private JarvisIntegrationIntents() { }

    static boolean createCalendarEvent(Context context, String title, long startMillis, long endMillis) {
        try {
            Intent event = new Intent(Intent.ACTION_INSERT);
            event.setData(CalendarContract.Events.CONTENT_URI);
            event.putExtra(CalendarContract.Events.TITLE, title);
            event.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis);
            event.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis);
            event.putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY);
            context.startActivity(Intent.createChooser(event, "Save calendar event"));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean composeEmail(Context context, String address, String subject, String body) {
        try {
            Uri destination = (address == null || address.trim().isEmpty())
                    ? Uri.parse("mailto:")
                    : Uri.fromParts("mailto", address.trim(), null);
            Intent email = new Intent(Intent.ACTION_SENDTO, destination);
            email.putExtra(Intent.EXTRA_SUBJECT, subject == null ? "" : subject);
            email.putExtra(Intent.EXTRA_TEXT, body == null ? "" : body);
            context.startActivity(Intent.createChooser(email, "Compose email"));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean shareTelegram(Context context, String message) {
        try {
            Intent telegram = new Intent(Intent.ACTION_SEND);
            telegram.setType("text/plain");
            telegram.putExtra(Intent.EXTRA_TEXT, message == null ? "" : message);
            telegram.setPackage("org.telegram.messenger");
            context.startActivity(telegram);
            return true;
        } catch (Exception ignored) {
            try {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, message == null ? "" : message);
                context.startActivity(Intent.createChooser(share, "Share message"));
                return true;
            } catch (Exception ignoredAgain) {
                return false;
            }
        }
    }

    static boolean openMapSearch(Context context, String destination) {
        if (destination == null || destination.trim().isEmpty()) return false;
        try {
            Uri location = Uri.parse("geo:0,0?q=" + Uri.encode(destination.trim()));
            Intent map = new Intent(Intent.ACTION_VIEW, location);
            context.startActivity(Intent.createChooser(map, "Open map"));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
