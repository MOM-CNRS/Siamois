package fr.siamois.utils;

import fr.siamois.annotations.ExecutionTimeLogger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ExecutionTimeAspect {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionTimeAspect.class);

    @Around("@annotation(executionTimeLogger)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint, ExecutionTimeLogger executionTimeLogger) throws Throwable {
        long start = System.currentTimeMillis();

        Object result = joinPoint.proceed();

        long duration = System.currentTimeMillis() - start;
        logger.info("⏱ Method [{}] executed in {} ms", joinPoint.getSignature(), duration);

        return result;
    }
}
