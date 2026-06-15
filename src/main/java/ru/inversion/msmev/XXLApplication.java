package ru.inversion.msmev;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ru.inversion.db.session.SessionEnvironment;

import javax.sql.DataSource;

@SpringBootApplication
@Slf4j
public class XXLApplication {

    /** */
    final static public String GATE_NAME = "xxl-gate";
    final static public String VERSION   = "Version 1.0.0";

    /** */
    private static volatile boolean GATE_INIT = false;

    @Autowired
    private DataSource dataSource;

    //@Autowired
    //private ApplicationContext context;
    /*
    @Bean
    public RequestHandler<?, ?> requestHandler() {
        return new XXLRequestHandler( dispatcher() );
    }

    @Bean
    public Dispatcher dispatcher() {
        return new Dispatcher();
    }
    */

    @PostConstruct
    private void initGate()
    {
       // log.info( "start init {} {} ... ", GATE_NAME, VERSION );

        try
        {
            if( !GATE_INIT)
            {
                SessionEnvironment.initialize(this.dataSource);
            }

            GATE_INIT = true;

        } catch (Throwable th) {
           // log.error("Error on init gate " + GATE_NAME, th) ;
            throw new RuntimeException("Error on init gate " + GATE_NAME, th);
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(XXLApplication.class, args);
    }
}
