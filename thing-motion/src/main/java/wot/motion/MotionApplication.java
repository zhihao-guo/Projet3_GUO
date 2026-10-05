package wot.motion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Independent virtual motion service. */
@SpringBootApplication
@EnableScheduling
public class MotionApplication {

    public static void main(String[] args) {
        SpringApplication.run(MotionApplication.class, args);
    }
}
