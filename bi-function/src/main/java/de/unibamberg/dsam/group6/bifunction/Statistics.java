package de.unibamberg.dsam.group6.bifunction;

import com.google.cloud.datastore.*;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;

public class Statistics implements HttpFunction {
    private static final Gson gson = new Gson();
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    private record Stats(Integer timestamp, String postalCode, Map<String, Integer> orderItems) {
        public boolean isValid() {
            return this.timestamp != null
                    && this.postalCode != null
                    && this.orderItems != null
                    && !this.orderItems.isEmpty();
        }
    }


    private static void write(BufferedWriter bw, String text) {
        try {
            bw.write(text);
        } catch (IOException ignore) {
        }
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        final var writer = response.getWriter();

        final var contentType = request.getContentType().orElse("");
        if (!contentType.equals("application/json")) {
            response.setStatusCode(400, "Bad content type!");
            return;
        }
        final var body = gson.fromJson(request.getReader(), Stats.class);

        if (!body.isValid()) {
            response.setStatusCode(400);
            return;
        }

        final var taskkey = datastore.newKeyFactory().setKind("stat").newKey("stat-value");

        final var statInstance = Entity.newBuilder(taskkey)
                .set("timestamp", body.timestamp)
                .set("orderItems", gson.toJson(body.orderItems))
                .set("postalCode", body.postalCode)
                .build();

        datastore.put(statInstance);
        write(writer, body.toString());
    }
}
