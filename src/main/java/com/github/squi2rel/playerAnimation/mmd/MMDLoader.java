package com.github.squi2rel.playerAnimation.mmd;

import com.github.squi2rel.playerAnimation.PlayerAnimation;
import com.github.squi2rel.playerAnimation.audio.AudioLoader;
import jp.nyatla.nymmd.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class MMDLoader {

    public static MMDResource load(String folderPath) throws Exception {
        File folder = new File(folderPath);

        short[] audio = null;
        List<File> pmdFiles = new ArrayList<>();
        List<File> vmdFiles = new ArrayList<>();
        File cameraFile = null;

        for (File file : Objects.requireNonNull(folder.listFiles())) {
            if (!file.isFile()) continue;
            String name = file.getName().toLowerCase();

            if (name.endsWith(".pmd")) {
                pmdFiles.add(file);
            } else if (name.endsWith(".vmd")) {
                vmdFiles.add(file);
            } else if (name.endsWith(".wav")) {
                audio = AudioLoader.load(file.toPath());
            }
        }

        long smallest = Long.MAX_VALUE;
        for (File file : vmdFiles) {
            long length = file.length();
            if (length < smallest) {
                smallest = length;
                cameraFile = file;
            }
        }
        PlayerAnimation.LOGGER.info("camera like file " + cameraFile);

        if (pmdFiles.isEmpty() || vmdFiles.isEmpty()) throw new Exception("Missing files");

        MMDCameraData camera = null;
        try (InputStream is = new FileInputStream(Objects.requireNonNull(cameraFile))) {
            int l = MMDCameraReader.readCameraLength(is);
            if (l != -1) {
                PlayerAnimation.LOGGER.info("is camera file " + cameraFile);
                camera = MMDCameraReader.readBody(is, l);
                vmdFiles.remove(cameraFile);
            }
        }

        if (pmdFiles.size() != 1 && pmdFiles.size() != vmdFiles.size()) throw new MmdException("Incomplete resource");

        Comparator<File> c = Comparator.comparingInt(f -> {
            String name = f.getName();
            String digits = name.replaceAll("\\D+", "");
            try {
                return digits.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        });
        pmdFiles.sort(c);
        vmdFiles.sort(c);

        List<MMDModelData> models = new ArrayList<>();
        for (int i = 0, l = vmdFiles.size(); i < l; i++) {
            File pmdFile = pmdFiles.get(pmdFiles.size() == 1 ? 0 : i);
            File vmdFile = vmdFiles.get(i);
            models.add(MMDModelData.create(new MmdPmdModel(pmdFile.getAbsolutePath()), new MmdVmdMotion(vmdFile.getAbsolutePath())));
        }

        return new MMDResource(models.toArray(new MMDModelData[0]), camera, audio);
    }

}
