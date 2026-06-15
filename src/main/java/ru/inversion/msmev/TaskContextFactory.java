package ru.inversion.msmev;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import ru.inversion.tc.TaskContext;

@Configuration
public class TaskContextFactory {
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public TaskContext taskContext() {
        return new TaskContext();
    }
}
