package org.rabix.db.transaction;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class JdbcTransactionInterceptor implements MethodInterceptor {

  private final static Logger logger = LoggerFactory.getLogger(JdbcTransactionInterceptor.class);

  @Inject
  private JdbcTransactionService transactionService;

  @Override
  public Object invoke(MethodInvocation method) throws Throwable {
    try {
      transactionService.begin();
      logger.debug("Start to invoke the method: " + method);

      Object result = method.proceed();
      logger.debug("Finish invoking the method: " + method);
      transactionService.commit();
      return result;
    } catch (Exception e) {
      logger.error("Failed to commit transaction!", e);
      try {
        transactionService.rollback();
      } catch (Exception ex) {
        logger.error("Cannot roll back transaction!", ex);
      }
      throw e;
    }
  }

}
