package org.overmesh.android;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MeshCore Open point-of-interest marker carried in a text message. */
final class MeshCoreMarker {
    private static final String NUMBER = "(-?[0-9]{1,3}(?:\\.[0-9]+)?)";
    private static final Pattern MARKER = Pattern.compile("m:" + NUMBER + "," + NUMBER + "\\|([^|\\p{Cntrl}]{1,100})\\|poi");
    final String sender, title;
    final double latitude, longitude;

    private MeshCoreMarker(String sender, String title, double latitude, double longitude) {
        this.sender = sender;
        this.title = title;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    static MeshCoreMarker parse(String sender, String text, boolean channel) {
        // MeshCore includes the advertised sender name in the channel message body.
        if (channel && !MARKER.matcher(text).matches()) {
            int prefix = text.indexOf(": ");
            if (prefix <= 0) return null;
            sender = text.substring(0, prefix);
            text = text.substring(prefix + 2);
        }
        Matcher match = MARKER.matcher(text);
        if (!match.matches()) return null;
        double lat = Double.parseDouble(match.group(1)), lon = Double.parseDouble(match.group(2));
        String title = match.group(3).trim();
        if (title.isEmpty() || !MapData.valid(lat, lon)) return null;
        return new MeshCoreMarker(sender, title, lat, lon);
    }
}
