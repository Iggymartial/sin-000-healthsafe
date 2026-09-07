package co.wethinkcode.healthsafe;

import java.io.InputStream;
import java.util.List;

import io.javalin.Javalin;

public class IngestionServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7030);

        app.get("/health", ctx -> ctx.result("OK"));

        // Cleaned once at startup and held in memory - this is a scaffold
        // exercise over a static CSV, not a live-updating data source, so
        // re-cleaning on every request would add cost for no benefit.
        List<WardRecord> cleanedWards = loadCleanedWards();

        app.get("/wards", ctx -> ctx.json(cleanedWards));
    }

    private static List<WardRecord> loadCleanedWards() {
        try (InputStream csv = IngestionServiceApp.class.getResourceAsStream("/wards-outdated.csv")) {
            if (csv == null) {
                throw new IllegalStateException("wards-outdated.csv not found on classpath");
            }
            return new WardCsvCleaner().clean(csv);
        } catch (Exception e) {
            // Fail loudly and immediately at startup rather than serving an
            // empty/broken /wards endpoint that looks like it's working.
            throw new RuntimeException("Failed to load and clean wards-outdated.csv", e);
        }
    }    
}
