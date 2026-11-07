package example.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SnapshotService {
    @Bean
    public ObjectMapper objectMapper() { return new ObjectMapper(); }

    public static void main(String[] args) { SpringApplication.run(SnapshotService.class, args); }
}
