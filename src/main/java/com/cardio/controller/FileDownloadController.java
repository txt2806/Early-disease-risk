package com.cardio.controller;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class FileDownloadController {

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam("file") String filePath) {
        try {
            // Prevent directory traversal attacks
            if (filePath == null || filePath.contains("..")) {
                return ResponseEntity.badRequest().build();
            }

            // Must be within uploads folder
            if (!filePath.startsWith("/uploads/")) {
                return ResponseEntity.badRequest().build();
            }

            String projectPath = new File(".").getAbsolutePath();
            if (projectPath.endsWith(".")) {
                projectPath = projectPath.substring(0, projectPath.length() - 1);
            }
            projectPath = projectPath.replace("\\", "/");
            if (!projectPath.endsWith("/")) {
                projectPath += "/";
            }

            // Remove leading /uploads/ from filePath to resolve within the uploads directory
            String relativePath = filePath.substring(1); // e.g. "uploads/blood/filename.pdf"
            Path fileStorageLocation = Paths.get(projectPath + relativePath).normalize();
            File file = fileStorageLocation.toFile();

            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(fileStorageLocation.toUri());

            // Determine content type
            String contentType = "application/octet-stream";
            try {
                contentType = java.nio.file.Files.probeContentType(fileStorageLocation);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }
            } catch (Exception ex) {
                // Keep default content type
            }

            String cleanFileName = file.getName();
            // Remove timestamp prefix from cleanFileName for user friendliness
            if (cleanFileName.contains("_")) {
                cleanFileName = cleanFileName.substring(cleanFileName.indexOf("_") + 1);
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + cleanFileName + "\"")
                    .body(resource);

        } catch (MalformedURLException ex) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
