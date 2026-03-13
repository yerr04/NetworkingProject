package config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class CommonConfig {
    public final int numberOfPreferredNeighbors;
    public final int unchokingIntervalSeconds;
    public final int optimisticUnchokingIntervalSeconds;
    public final String fileName;
    public final int fileSize;
    public final int pieceSize;

    private CommonConfig(
            int numberOfPreferredNeighbors,
            int unchokingIntervalSeconds,
            int optimisticUnchokingIntervalSeconds,
            String fileName,
            int fileSize,
            int pieceSize
    ) {
        this.numberOfPreferredNeighbors = numberOfPreferredNeighbors;
        this.unchokingIntervalSeconds = unchokingIntervalSeconds;
        this.optimisticUnchokingIntervalSeconds = optimisticUnchokingIntervalSeconds;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.pieceSize = pieceSize;
    }

    public static CommonConfig load(File commonCfg) throws IOException {
        if (commonCfg == null) throw new IllegalArgumentException("Common.cfg file is null");
        if (!commonCfg.exists()) throw new IOException("Missing Common.cfg at: " + commonCfg.getPath());

        Map<String, String> kv = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(commonCfg))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 2) continue;
                String key = parts[0].trim();
                String value = parts[1].trim();
                kv.put(key, value);
            }
        }

        int nPref = requirePositiveInt(kv, "NumberOfPreferredNeighbors");
        int unchoke = requirePositiveInt(kv, "UnchokingInterval");
        int opt = requirePositiveInt(kv, "OptimisticUnchokingInterval");
        String fileName = requireNonEmpty(kv, "FileName");
        int fileSize = requirePositiveInt(kv, "FileSize");
        int pieceSize = requirePositiveInt(kv, "PieceSize");

        return new CommonConfig(nPref, unchoke, opt, fileName, fileSize, pieceSize);
    }

    private static int requirePositiveInt(Map<String, String> kv, String key) {
        String v = kv.get(key);
        if (v == null) throw new IllegalArgumentException("Missing Common.cfg key: " + key);
        try {
            int parsed = Integer.parseInt(v);
            if (parsed <= 0) throw new IllegalArgumentException("Common.cfg key " + key + " must be > 0");
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Common.cfg key " + key + " must be an integer");
        }
    }

    private static String requireNonEmpty(Map<String, String> kv, String key) {
        String v = kv.get(key);
        if (v == null) throw new IllegalArgumentException("Missing Common.cfg key: " + key);
        v = v.trim();
        if (v.isEmpty()) throw new IllegalArgumentException("Common.cfg key " + key + " must be non-empty");
        return v;
    }
}
