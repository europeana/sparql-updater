package europeana.sparql.updater;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point of the application
 */
@SpringBootApplication
public class UpdaterApplication {

    /**
     * Start the application
     * @param args command line arguments (see also UpdaterSettings class)
     */
    public static void main(String[] args) {
        SpringApplication.run(UpdaterApplication.class, args);
    }
}
