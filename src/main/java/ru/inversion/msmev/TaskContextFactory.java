package ru.inversion.msmev;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import ru.inversion.tc.TaskContext;

@Configuration
public class TaskContextFactory
{
    /** Существующий prototype для всех обычных XXL-потоков. */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public TaskContext taskContext()
    {
        return new TaskContext();
    }

   /**
     * Отдельный TaskContext под tech-пользователем.
     *  Для логина пользователей XXI.
     *  Строка подключения берется из "основного" пула
     */
    public TaskContext create( String login, String password )
    {
        return new TaskContext( login, password, null );
    }
}