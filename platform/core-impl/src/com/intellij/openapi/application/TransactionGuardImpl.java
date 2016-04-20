/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author peter
 */
public class TransactionGuardImpl extends TransactionGuard {
  private final Queue<Transaction> myQueue = new LinkedBlockingQueue<Transaction>();
  private final Map<ModalityState, TransactionIdImpl> myModality2Transaction = ContainerUtil.createConcurrentWeakMap();
  private final Set<ModalityState> myWriteSafeModalities = Collections.newSetFromMap(ContainerUtil.<ModalityState, Boolean>createConcurrentWeakMap());
  private TransactionIdImpl myCurrentTransaction;
  private boolean myWritingAllowed;

  public TransactionGuardImpl() {
    myWriteSafeModalities.add(ModalityState.NON_MODAL);
  }

  @NotNull
  private AccessToken startTransactionUnchecked() {
    final TransactionIdImpl prevTransaction = myCurrentTransaction;
    final boolean wasWritingAllowed = myWritingAllowed;

    myWritingAllowed = true;
    myCurrentTransaction = new TransactionIdImpl();

    return new AccessToken() {
      @Override
      public void finish() {
        Queue<Transaction> queue = getQueue(prevTransaction);
        queue.addAll(myCurrentTransaction.myQueue);
        if (!queue.isEmpty()) {
          pollQueueLater();
        }

        myWritingAllowed = wasWritingAllowed;
        myCurrentTransaction = prevTransaction;
      }
    };
  }

  @NotNull
  private Queue<Transaction> getQueue(@Nullable TransactionIdImpl transaction) {
    if (transaction == null) {
      return myQueue;
    }
    if (myCurrentTransaction != null && transaction.myStartCounter > myCurrentTransaction.myStartCounter) {
      // transaction is finished already, it makes no sense to add to its queue
      return myCurrentTransaction.myQueue;
    }
    return transaction.myQueue;
  }

  private void pollQueueLater() {
    //todo replace with SwingUtilities when write actions are required to run under a guard
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        Queue<Transaction> queue = getQueue(myCurrentTransaction);
        Transaction next = queue.peek();
        if (next != null && canRunTransactionNow(next, false)) {
          queue.remove();
          runSyncTransaction(next);
        }
      }
    });
  }

  private void runSyncTransaction(@NotNull Transaction transaction) {
    if (Disposer.isDisposed(transaction.parentDisposable)) return;

    AccessToken token = startTransactionUnchecked();
    try {
      transaction.runnable.run();
    }
    finally {
      token.finish();
    }
  }

  @Override
  public void submitTransaction(@NotNull Disposable parentDisposable, @Nullable TransactionId expectedContext, @NotNull Runnable _transaction) {
    final TransactionIdImpl expectedId = (TransactionIdImpl)expectedContext;
    final Transaction transaction = new Transaction(_transaction, expectedId, parentDisposable);
    final Application app = ApplicationManager.getApplication();
    final boolean isDispatchThread = app.isDispatchThread();
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (canRunTransactionNow(transaction, isDispatchThread)) {
          runSyncTransaction(transaction);
        }
        else {
          getQueue(expectedId).offer(transaction);
          pollQueueLater();
        }
      }
    };

    if (isDispatchThread) {
      runnable.run();
    } else {
      //todo add ModalityState.any() when write actions are required to run under a guard
      app.invokeLater(runnable);
    }
  }

  private boolean canRunTransactionNow(Transaction transaction, boolean sync) {
    TransactionIdImpl currentId = myCurrentTransaction;
    if (currentId == null) {
      return true;
    }

    if (sync && !myWritingAllowed) {
      return false;
    }

    return transaction.expectedContext != null && currentId.myStartCounter <= transaction.expectedContext.myStartCounter;
  }

  @Override
  public void submitTransactionAndWait(@NotNull final Runnable runnable) throws ProcessCanceledException {
    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      Transaction transaction = new Transaction(runnable, getContextTransaction(), app);
      if (!canRunTransactionNow(transaction, true)) {
        throw new AssertionError("Cannot run synchronous submitTransactionAndWait from invokeLater. " +
                                 "Please use asynchronous submit*Transaction. " +
                                 "See TransactionGuard FAQ for details.");
      }
      runSyncTransaction(transaction);
      return;
    }

    assert !app.isReadAccessAllowed() : "submitTransactionAndWait should not be invoked from a read action";
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final Throwable[] exception = {null};
    submitTransaction(Disposer.newDisposable("never disposed"), getContextTransaction(), new Runnable() {
      @Override
      public void run() {
        try {
          runnable.run();
        }
        catch (Throwable e) {
          exception[0] = e;
        }
        finally {
          semaphore.up();
        }
      }
    });
    semaphore.waitFor();
    if (exception[0] != null) {
      throw new RuntimeException(exception[0]);
    }
  }

  /**
   * An absolutely guru method!<p/>
   *
   * Executes the given code and marks it as a user activity, to allow write actions to be run without requiring transactions.
   * This is only to be called from UI infrastructure, during InputEvent processing and wrap the point where the control
   * goes to custom input event handlers for the first time.<p/>
   *
   * If you wish to invoke some actionPerformed,
   * please consider using {@code ActionManager.tryToExecute()} instead, or ensure in some other way that the action is enabled
   * and can be invoked in the current modality state.
   */
  public void performUserActivity(Runnable activity) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    AccessToken token = startActivity(true);
    try {
      activity.run();
    }
    finally {
      token.finish();
    }
  }

  /**
   * An absolutely guru method, only intended to be used from Swing event processing. Please consult Peter if you think you need to invoke this.
   */
  @NotNull
  public AccessToken startActivity(boolean userActivity) {
    if (myWritingAllowed == userActivity) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }

    final boolean prev = myWritingAllowed;
    myWritingAllowed = userActivity;
    return new AccessToken() {
      @Override
      public void finish() {
        myWritingAllowed = prev;
      }
    };
  }

  public boolean isWriteActionAllowed() {
    return !Registry.is("ide.require.transaction.for.model.changes", false) || myWritingAllowed;
  }

  @Override
  public void submitTransactionLater(@NotNull final Disposable parentDisposable, @NotNull final Runnable transaction) {
    final TransactionIdImpl id = getContextTransaction();
    Application app = ApplicationManager.getApplication();
    app.invokeLater(new Runnable() {
      @Override
      public void run() {
        submitTransaction(parentDisposable, id, transaction);
      }
    });
  }

  @Override
  public TransactionIdImpl getContextTransaction() {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
      return indicator != null ? myModality2Transaction.get(indicator.getModalityState()) : null;
    }

    return myWritingAllowed ? myCurrentTransaction : null;
  }

  public void enteredModality(@NotNull ModalityState modality) {
    TransactionIdImpl contextTransaction = getContextTransaction();
    if (contextTransaction != null) {
      myModality2Transaction.put(modality, contextTransaction);
    }
    if (myWritingAllowed) {
      myWriteSafeModalities.add(modality);
    }
  }

  @Nullable
  public TransactionIdImpl getModalityTransaction(@NotNull ModalityState modalityState) {
    return myModality2Transaction.get(modalityState);
  }

  @NotNull
  public Runnable wrapLaterInvocation(@NotNull final Runnable runnable, @NotNull ModalityState modalityState) {
    if (myWriteSafeModalities.contains(modalityState)) {
      return new Runnable() {
        @Override
        public void run() {
          final boolean prev = myWritingAllowed;
          myWritingAllowed = true;
          try {
            runnable.run();
          } finally {
            myWritingAllowed = prev;
          }
        }
      };
    }

    return runnable;
  }

  private static class Transaction {
    @NotNull  final Runnable runnable;
    @Nullable final TransactionIdImpl expectedContext;
    @NotNull  final Disposable parentDisposable;

    Transaction(@NotNull Runnable runnable, @Nullable TransactionIdImpl expectedContext, @NotNull Disposable parentDisposable) {
      this.runnable = runnable;
      this.expectedContext = expectedContext;
      this.parentDisposable = parentDisposable;
    }
  }

  private static class TransactionIdImpl implements TransactionId {
    private static final AtomicLong ourTransactionCounter = new AtomicLong();
    final long myStartCounter = ourTransactionCounter.getAndIncrement();
    final Queue<Transaction> myQueue = new LinkedBlockingQueue<Transaction>();

    @Override
    public String toString() {
      return "Transaction " + myStartCounter;
    }
  }
}
