package com.tycooncraft.tyc2.qa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Structured assertion, metric, and properties/JSON report recorder for Forge 1.19.2 QA.
 */
final class TyC2QaAssert {

    private final String label;
    private final Map<String, String> checks = new LinkedHashMap<>();
    private final Map<String, String> metrics = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();

    TyC2QaAssert(String label) {
        this.label = label;
    }

    void check(String key, boolean condition, String detail) {
        if (condition) {
            checks.put(key, "PASS (" + detail + ")");
            TyC2QaMod.LOGGER.info("[TYC2-QA] PASS {}: {}", key, detail);
        } else {
            String msg = key + ": " + detail;
            checks.put(key, "FAIL (" + detail + ")");
            failures.add(msg);
            TyC2QaMod.LOGGER.error("[TYC2-QA] FAIL {}: {}", key, detail);
            throw new AssertionError("[TYC2-QA] " + msg);
        }
    }

    void checkEquals(String key, Object expected, Object actual) {
        boolean eq = expected == null ? actual == null : expected.equals(actual);
        check(key, eq, "expected=" + expected + ", actual=" + actual);
    }

    void metric(String key, Object value) {
        metrics.put(key, String.valueOf(value));
    }

    int passedCount() {
        return checks.size() - failures.size();
    }

    int failedCount() {
        return failures.size();
    }

    void writePassedReport(Path propsFile, Map<String, String> extras) throws IOException {
        Properties props = new Properties();
        props.setProperty("label", label);
        props.setProperty("status", failures.isEmpty() ? "PASSED" : "FAILED");
        props.setProperty("passedChecks", Integer.toString(passedCount()));
        props.setProperty("failedChecks", Integer.toString(failedCount()));
        props.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
        for (Map.Entry<String, String> e : checks.entrySet()) {
            props.setProperty("check." + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : metrics.entrySet()) {
            props.setProperty("metric." + e.getKey(), e.getValue());
        }
        if (extras != null) {
            for (Map.Entry<String, String> e : extras.entrySet()) {
                props.setProperty(e.getKey(), e.getValue());
            }
        }
        try (Writer w = Files.newBufferedWriter(propsFile)) {
            props.store(w, "TycoonCraft 2 Forge 1.19.2 QA Report (" + label + ")");
        }

        JsonObject json = new JsonObject();
        json.addProperty("label", label);
        json.addProperty("status", failures.isEmpty() ? "PASSED" : "FAILED");
        json.addProperty("passedChecks", passedCount());
        json.addProperty("failedChecks", failedCount());
        json.addProperty("pid", ProcessHandle.current().pid());
        JsonObject mObj = new JsonObject();
        metrics.forEach(mObj::addProperty);
        json.add("metrics", mObj);
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, String> e : checks.entrySet()) {
            JsonObject row = new JsonObject();
            row.addProperty("name", e.getKey());
            row.addProperty("result", e.getValue());
            arr.add(row);
        }
        json.add("checks", arr);
        Path jsonFile = Path.of(propsFile.toString().replace(".properties", ".json"));
        Files.writeString(jsonFile, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
    }

    void writeFailureReport(Path propsFile, Path failureTxt, Throwable error) {
        try {
            Properties props = new Properties();
            props.setProperty("label", label);
            props.setProperty("status", "FAILED");
            props.setProperty("passedChecks", Integer.toString(passedCount()));
            props.setProperty("failedChecks", Integer.toString(Math.max(1, failedCount())));
            props.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
            props.setProperty("error", String.valueOf(error));
            for (Map.Entry<String, String> e : checks.entrySet()) {
                props.setProperty("check." + e.getKey(), e.getValue());
            }
            for (Map.Entry<String, String> e : metrics.entrySet()) {
                props.setProperty("metric." + e.getKey(), e.getValue());
            }
            try (Writer w = Files.newBufferedWriter(propsFile)) {
                props.store(w, "TycoonCraft 2 Forge 1.19.2 QA Failure (" + label + ")");
            }
            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            StringBuilder sb = new StringBuilder();
            sb.append("label=").append(label).append('\n');
            sb.append("passedChecks=").append(passedCount()).append('\n');
            sb.append("failedChecks=").append(Math.max(1, failedCount())).append('\n');
            sb.append("error=").append(error).append('\n');
            sb.append("stackTrace:\n").append(sw).append('\n');
            sb.append("completedChecks:\n");
            for (Map.Entry<String, String> e : checks.entrySet()) {
                sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
            }
            Files.writeString(failureTxt, sb.toString());
        } catch (IOException ioe) {
            TyC2QaMod.LOGGER.error("[TYC2-QA] Failed writing failure report", ioe);
        }
    }
}
