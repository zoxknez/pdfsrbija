package stirling.software.SPDF.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SrbProfileService {

    private static final Path PROFILE_PATH = Paths.get("configs", "serbia-profile.json");
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReentrantLock lock = new ReentrantLock();

    public static class SrbProfile {
        public String companyName;
        public String pib;
        public String iban;
        public String address;
        public String city;
        public String postalCode;
        public String email;
        public String phone;
        public String defaultCurrency = "RSD";
        public String defaultModel = "97";
        public String logoPath; // relative path if served statically
    }

    public SrbProfile load() {
        lock.lock();
        try {
            if (Files.exists(PROFILE_PATH)) {
                try {
                    return mapper.readValue(PROFILE_PATH.toFile(), SrbProfile.class);
                } catch (IOException e) {
                    // return empty profile on parse error
                    return new SrbProfile();
                }
            }
            return new SrbProfile();
        } finally {
            lock.unlock();
        }
    }

    public SrbProfile save(SrbProfile profile) throws IOException {
        lock.lock();
        try {
            Files.createDirectories(PROFILE_PATH.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(PROFILE_PATH.toFile(), profile);
            return profile;
        } finally {
            lock.unlock();
        }
    }
}
