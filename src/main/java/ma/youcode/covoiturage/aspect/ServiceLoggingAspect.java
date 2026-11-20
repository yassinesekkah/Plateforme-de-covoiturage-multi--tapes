package ma.youcode.covoiturage.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ServiceLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ServiceLoggingAspect.class);

    @Before("execution(public * ma.youcode.covoiturage.service.impl.*.*(..))")
    public void logBefore(JoinPoint joinPoint) {
        log.info("Debut : {}", joinPoint.getSignature().toShortString());
    }

    @AfterReturning("execution(public * ma.youcode.covoiturage.service.impl.*.*(..))")
    public void logAfterReturning(JoinPoint joinPoint) {
        log.info("Fin : {}", joinPoint.getSignature().toShortString());
    }
}
