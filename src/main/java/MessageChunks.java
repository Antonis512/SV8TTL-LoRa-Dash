package org.overmesh.android;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Splits only between Unicode grapheme clusters; no recipient-side reassembly assumed. */
final class MessageChunks {
    static List<String> split(String text,int limit) {
        if(limit<1||text==null||text.trim().isEmpty()||text.indexOf('\0')>=0)
            throw new IllegalArgumentException("Enter text and wait for the radio's message limit");
        List<String> result=new ArrayList<>();StringBuilder chunk=new StringBuilder();int bytes=0;
        java.util.regex.Matcher clusters=java.util.regex.Pattern.compile("\\X").matcher(text.trim());
        while(clusters.find()) {
            String cluster=clusters.group();int size=cluster.getBytes(StandardCharsets.UTF_8).length;
            if(size>limit)throw new IllegalArgumentException("A single emoji or character sequence exceeds this radio's message limit");
            if(bytes+size>limit){result.add(chunk.toString());chunk.setLength(0);bytes=0;}
            chunk.append(cluster);bytes+=size;
        }
        if(chunk.length()>0)result.add(chunk.toString());return result;
    }
}
