package org.overmesh.android;
import java.nio.charset.StandardCharsets;
import java.util.List;
public final class MessageChunksSmoke {
    public static void main(String[] args) {
        for(int limit:new int[]{150,200}) for(String text:new String[]{"αβγ ".repeat(100),"👍🏽hello ".repeat(30),"👨‍👩‍👧‍👦 ".repeat(30),"e\u0301".repeat(100),"🇬🇷".repeat(100)}) {
            List<String> parts=MessageChunks.split(text,limit);
            if(!String.join("",parts).equals(text.trim()))throw new AssertionError("Lost text");
            for(String part:parts) {
                if(part.getBytes(StandardCharsets.UTF_8).length>limit)throw new AssertionError("Oversize");
                if(part.endsWith("\u200d")||Character.isLowSurrogate(part.charAt(0)))throw new AssertionError("Broken cluster");
            }
        }
        if(MessageChunks.split("x".repeat(150),150).size()!=1||MessageChunks.split("x".repeat(151),150).size()!=2)throw new AssertionError("Boundary");
        try{MessageChunks.split("👨‍👩‍👧‍👦",4);throw new AssertionError("Must reject indivisible cluster");}catch(IllegalArgumentException expected){}
        if(MessageChunks.split("x".repeat(210),200).size()!=2||MessageChunks.split("👨‍👩‍👧‍👦".repeat(17),200).size()!=3)throw new AssertionError("MT part counts");
        System.out.println("UTF-8 chunking and grapheme boundary checks passed");
    }
}
