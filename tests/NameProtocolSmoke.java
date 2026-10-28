package org.overmesh.android;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class NameProtocolSmoke {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>();
        List<String> roles = new ArrayList<>(), models = new ArrayList<>();
        RadioProtocol.Events events = (RadioProtocol.Events) Proxy.newProxyInstance(
                RadioProtocol.Events.class.getClassLoader(), new Class<?>[]{RadioProtocol.Events.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("node")) {names.add((String)values[1]);roles.add((String)values[2]);models.add((String)values[3]);}
                    return null;
                });
        MeshtasticProtocol protocol = new MeshtasticProtocol();
        // The short name can arrive first or after a known long name.
        send(protocol, events, "", "AB");
        send(protocol, events, "Named station", "AB");
        send(protocol, events, "", "CD");
        send(protocol, events, "Renamed station", "EF");
        send(protocol, events, "", "");
        if (!names.equals(List.of("AB", "Named station", "Named station", "Renamed station", "Renamed station")))
            throw new AssertionError(names);
        // Also resolve names carried in a live NODEINFO_APP packet.
        byte[] user = new ProtoWire.Writer().bytes(3, "GH".getBytes(StandardCharsets.UTF_8))
                .bytes(2, "Live station".getBytes(StandardCharsets.UTF_8)).build();
        byte[] data = new ProtoWire.Writer().varint(1, 4).bytes(2, user).build();
        byte[] packet = new ProtoWire.Writer().fixed32(1, 0xabcdef12L).bytes(4, data).build();
        protocol.receive(new ProtoWire.Writer().bytes(2, packet).build(), events);
        if (!names.get(names.size()-1).equals("Live station")) throw new AssertionError(names);
        if (!roles.get(roles.size()-1).equals("Client")) throw new AssertionError("Omitted live role must default to Client");
        byte[] hardware = new ProtoWire.Writer().varint(5,69).build();
        protocol.receive(new ProtoWire.Writer().bytes(4,new ProtoWire.Writer().varint(1,0xabcdef13L).bytes(2,hardware).build()).build(),events);
        if (!models.get(models.size()-1).equals("HELTEC MESH NODE T114")) throw new AssertionError(models);
        if (!roles.get(roles.size()-1).equals("Client")) throw new AssertionError(roles);
        System.out.println("Name protocol regression passed");
    }
    static void send(MeshtasticProtocol protocol, RadioProtocol.Events events, String name, String shortName) {
        byte[] user = new ProtoWire.Writer().bytes(3, shortName.getBytes(StandardCharsets.UTF_8))
                .bytes(2, name.getBytes(StandardCharsets.UTF_8)).build();
        byte[] info = new ProtoWire.Writer().varint(1, 0xabcdef12L).bytes(2, user).build();
        protocol.receive(new ProtoWire.Writer().bytes(4, info).build(), events);
    }
}
