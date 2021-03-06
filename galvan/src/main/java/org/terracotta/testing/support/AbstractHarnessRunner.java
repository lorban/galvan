/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terracotta.testing.support;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.terracotta.testing.api.ITestClusterConfiguration;
import org.terracotta.testing.api.ITestMaster;
import org.terracotta.testing.common.Assert;
import org.terracotta.testing.logging.VerboseLogger;
import org.terracotta.testing.logging.VerboseManager;
import org.terracotta.testing.master.DebugOptions;
import org.terracotta.testing.master.EnvironmentOptions;


public abstract class AbstractHarnessRunner<C extends ITestClusterConfiguration> extends Runner {
  private final AbstractHarnessTest<C> testCase;

  @SuppressWarnings("unchecked")
  public AbstractHarnessRunner(Class<?> testClass) throws InstantiationException, IllegalAccessException {
    // We will eagerly instantiate the test class in order to assume that it is the right type, later.
    this.testCase = AbstractHarnessTest.class.cast(testClass.newInstance());
  }


  @Override
  public Description getDescription() {
    return Description.createTestDescription(this.testCase.getClass(), this.testCase.getName());
  }

  @Override
  public void run(RunNotifier notifier) {
    Description testDescription = getDescription();
    notifier.fireTestStarted(testDescription);
    
    // Get the system properties we require.
    String allTestParentDirectory = System.getProperty("kitTestDirectory");
    String thisTestName = this.testCase.getName();
    EnvironmentOptions environmentOptions = new EnvironmentOptions();
    environmentOptions.clientClassPath = System.getProperty("java.class.path");
    environmentOptions.serverInstallDirectory = System.getProperty("kitInstallationPath");
    environmentOptions.testParentDirectory = allTestParentDirectory + File.separator + thisTestName;
    Assert.assertTrue(environmentOptions.isValid());
    
    // Get the test master implementation.
    ITestMaster<C> masterClass = this.testCase.getTestMaster();
    
    DebugOptions debugOptions = new DebugOptions();
    debugOptions.setupClientDebugPort = readIntProperty("setupClientDebugPort");
    debugOptions.destroyClientDebugPort = readIntProperty("destroyClientDebugPort");
    debugOptions.testClientDebugPortStart = readIntProperty("testClientDebugPortStart");
    debugOptions.serverDebugPortStart = readIntProperty("serverDebugPortStart");
    
    // Configure our verbose settings.
    // TODO:  Provide a property, etc, to change these defaults.
    VerboseLogger harnessLogger = new VerboseLogger(System.out, System.err);
    // By default, we don't log the file helpers since they are too verbose.
    VerboseLogger fileHelpersLogger = new VerboseLogger(null, System.err);
    VerboseLogger clientLogger = new VerboseLogger(System.out, System.err);
    VerboseLogger serverLogger = new VerboseLogger(System.out, System.err);
    VerboseManager verboseManager = new VerboseManager("", harnessLogger, fileHelpersLogger, clientLogger, serverLogger);
    
    // We will only succeed or fail.
    Throwable error = null;
    try {
      boolean wasCompleteSuccess = runTest(environmentOptions, masterClass, debugOptions, verboseManager);
      if (wasCompleteSuccess) {
        error = null;
      } else {
        // This was a failure without any other information so just create a generic exception.
        error = new Exception("Test failed without exception");
      }
    } catch (FileNotFoundException e) {
      error = e;
    } catch (IOException e) {
      error = e;
    } catch (InterruptedException e) {
      error = e;
    }
    // Determine how to handle the result.
    try {
      this.testCase.interpretResult(error);
      
      // Success.
      notifier.fireTestFinished(testDescription);
    } catch (Throwable t) {
      // Failure.
      notifier.fireTestFailure(new Failure(testDescription, t));
    }
  }

  private int readIntProperty(String propertyName) {
    int result = 0;
    String value = System.getProperty(propertyName);
    if (null != value) {
      try {
        result = Integer.parseInt(value);
      } catch (NumberFormatException e) {
        // Means it wasn't a number so we will treat that like it wasn't there.
      }
    }
    return result;
  }

  protected abstract boolean runTest(EnvironmentOptions environmentOptions, ITestMaster<C> masterClass, DebugOptions debugOptions, VerboseManager verboseManager) throws IOException, FileNotFoundException, InterruptedException;
}
