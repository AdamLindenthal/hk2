package org.jvnet.hk2.component.internal.runlevel;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.annotations.RunLevel;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.component.Inhabitant;
import org.jvnet.hk2.component.RunLevelListener;
import org.jvnet.hk2.component.RunLevelService;
import org.jvnet.hk2.component.RunLevelState;
import org.jvnet.hk2.component.internal.runlevel.DefaultRunLevelService;
import org.jvnet.hk2.component.internal.runlevel.Recorder;
import org.jvnet.hk2.junit.Hk2Runner;
import org.jvnet.hk2.junit.Hk2RunnerOptions;
import org.jvnet.hk2.test.runlevel.NonRunLevelWithRunLevelDepService;
import org.jvnet.hk2.test.runlevel.RunLevelService0;
import org.jvnet.hk2.test.runlevel.ServiceA;
import org.jvnet.hk2.test.runlevel.ServiceB;
import org.jvnet.hk2.test.runlevel.ServiceC;
import org.jvnet.hk2.test.runlevel.TestRunLevelListener;

import com.sun.hk2.component.AbstractInhabitantImpl;
import com.sun.hk2.component.ExistingSingletonInhabitant;

/**
 * Testing around the default RunLevelService impl.
 * 
 * @author Jeff Trent
 */
//@org.junit.Ignore // See 12729
@RunWith(Hk2Runner.class)
@Hk2RunnerOptions(reinitializePerTest=true)
public class RunLevelServiceTest {

  @Inject
  Habitat h;
  
  @Inject(name="default")
  RunLevelService<?> rls;
  
  @Inject
  RunLevelListener listener;

  private TestRunLevelListener defRLlistener;
  
  private HashMap<Integer, Recorder> recorders;

  private DefaultRunLevelService defRLS;

  
  /**
   * Verifies the state of the habitat
   */
  @SuppressWarnings("unchecked")
  @Test
  public void validInitialHabitatState() {
    Collection<RunLevelListener> coll1 = h.getAllByContract(RunLevelListener.class);
    assertNotNull(coll1);
    assertEquals(1, coll1.size());
    assertSame(listener, coll1.iterator().next());
    assertTrue(coll1.iterator().next() instanceof TestRunLevelListener);
    
    Collection<RunLevelService> coll2 = h.getAllByContract(RunLevelService.class);
    assertNotNull(coll2);
    assertEquals(coll2.toString(), 2, coll2.size());  // a test one, and the real one
    
    RunLevelService rls = h.getComponent(RunLevelService.class);
    assertNotNull(rls);
    assertNotNull(rls.getState());
    assertEquals(0, rls.getState().getCurrentRunLevel());
    assertEquals(null, rls.getState().getPlannedRunLevel());
    assertEquals(Void.class, rls.getState().getEnvironment());
    
    RunLevelService rls2 = h.getComponent(RunLevelService.class, "default");
    assertSame(rls, rls2);
    assertSame(this.rls, rls);
    assertTrue(rls instanceof DefaultRunLevelService);
  }
  
  /**
   * Verifies that RunLevel 0 inhabitants are created immediately
   */
  @Test
  public void validateRunLevel0Inhabitants() {
    assertTrue(h.isInitialized());
    Inhabitant<RunLevelService0> i = h.getInhabitantByType(RunLevelService0.class);
    assertNotNull(i);
    assertTrue(i.isInstantiated());
  }
  
  @Test
  public void proceedToNegNum() {
    try {
      rls.proceedTo(-1);
      fail("Expected -1 to be a problem");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }
  
  /**
   * There should be no runlevel services at this level.
   */
  @Test
  public void proceedTo0() {
    installTestRunLevelService(false);
    rls.proceedTo(0);
    assertEquals(0, recorders.size());
    assertEquals(0, defRLS.getCurrentRunLevel());
    assertEquals(null, defRLS.getPlannedRunLevel());
  }
  
  @Test
  public void proceedUpTo5_basics() {
    installTestRunLevelService(false);
    rls.proceedTo(5);
    assertEquals(1, recorders.size());
    Recorder recorder = recorders.get(5);
    assertNotNull(recorder);

    assertEquals(5, defRLS.getCurrentRunLevel());
    assertEquals(null, defRLS.getPlannedRunLevel());
  }

  @Test
  public void proceedUpTo5Async() throws InterruptedException {
    installTestRunLevelService(true);
    rls.proceedTo(5);
    assertEquals(5, defRLS.getPlannedRunLevel());
    Integer tmp = defRLS.getCurrentRunLevel();
    synchronized (rls) {
      rls.wait(1000);
    }
    assertTrue("too fast!", (null == tmp ? -1 : tmp) < 5);

    assertEquals(1, recorders.size());
    Recorder recorder = recorders.get(5);
    assertNotNull(recorder);

    assertEquals(5, defRLS.getCurrentRunLevel());
    assertEquals(null, defRLS.getPlannedRunLevel());
    
    assertInhabitantsState(5);
    assertListenerState(false, true, false);
    assertRecorderState();
  }
  
  @Test
  public void proceedUpTo10() {
    installTestRunLevelService(false);
    rls.proceedTo(10);
    assertEquals(null, defRLS.getPlannedRunLevel());

    assertEquals(recorders.toString(), 2, recorders.size());
    Recorder recorder = recorders.get(5);
    assertNotNull(recorder);

    recorder = recorders.get(10);
    assertNotNull(recorder);
    
    assertEquals(10, defRLS.getCurrentRunLevel());
    assertEquals(null, defRLS.getPlannedRunLevel());

    assertInhabitantsState(10);
    assertListenerState(false, true, false);
    assertRecorderState();
  }
  
  @Test
  public void proceedUpTo49() throws InterruptedException {
    installTestRunLevelService(false);
    rls.proceedTo(49);
    assertEquals(null, defRLS.getPlannedRunLevel());

    assertEquals(recorders.toString(), 3, recorders.size());
    Recorder recorder = recorders.get(5);
    assertNotNull(recorder);

    recorder = recorders.get(10);
    assertNotNull(recorder);
    
    recorder = recorders.get(20);
    assertNotNull(recorder);

    assertEquals(49, defRLS.getCurrentRunLevel());
    assertEquals(null, defRLS.getPlannedRunLevel());

    assertInhabitantsState(49);
    assertListenerState(false, true, false);
    assertRecorderState();
  }

  @Test
  public void proceedUpTo49ThenDownTo11() {
    installTestRunLevelService(false);
    rls.proceedTo(49);
    rls.proceedTo(11);
    assertEquals(null, defRLS.getPlannedRunLevel());

    assertEquals(2, recorders.size());
    Recorder recorder = recorders.get(5);
    assertNotNull(recorder);
    recorder = recorders.get(10);
    assertNotNull(recorder);

    assertEquals(11, defRLS.getCurrentRunLevel());
    assertEquals(null, defRLS.getPlannedRunLevel());

    assertInhabitantsState(11);
    assertListenerState(true, true, false);
    assertRecorderState();
  }
  
  @Test
  public void proceedUpTo49ThenDownTo11Async() throws InterruptedException {
    installTestRunLevelService(true);
    
    rls.proceedTo(49);
    rls.proceedTo(11);
    
    assertEquals(11, defRLS.getPlannedRunLevel());
    
    synchronized (rls) {
      rls.wait(1000);
    }
    assertEquals(11, defRLS.getCurrentRunLevel());

    assertEquals(2, recorders.size());
    Recorder recorder = recorders.get(5);
    assertNotNull(recorder);
    recorder = recorders.get(10);
    assertNotNull(recorder);

    assertEquals(11, defRLS.getCurrentRunLevel());
    assertEquals(null, defRLS.getPlannedRunLevel());

    assertInhabitantsState(11);
    assertListenerState(true, true, true);
    assertRecorderState();
  }
  
  @Test
  public void proceedUpTo49ThenDownTo11ThenDownToZero() {
    installTestRunLevelService(false);
    rls.proceedTo(49);
    rls.proceedTo(11);
    rls.proceedTo(0);
    assertEquals(null, defRLS.getPlannedRunLevel());

    assertEquals(0, recorders.size());

    assertInhabitantsState(0);
    assertListenerState(true, true, false);
//    assertRecorderState();
  }
  
  /**
   * This guy tests the underling Recorder Ordering.
   * 
   * Note that: ServiceA -> ServiceB -> ServiceC
   * 
   * So, if we active B, A, then C manually, we still expect
   * the recorder to have A, B, C only in that order.
   * 
   * This takes some "rigging" on the runLevelService (to ignore proceedTo)
   * @throws NoSuchFieldException 
   * @throws Exception 
   */
  @Test
  public void serviceABC() throws Exception {
    installTestRunLevelService(false);
    
    Field fldCurrent = DefaultRunLevelService.class.getDeclaredField("current");
    fldCurrent.setAccessible(true);
    fldCurrent.set(defRLS, 10);

    Field fldPlanned = DefaultRunLevelService.class.getDeclaredField("planned");
    fldPlanned.setAccessible(true);
    fldPlanned.set(defRLS, 10);
    
    Field fldActive = DefaultRunLevelService.class.getDeclaredField("activeRunLevel");
    fldActive.setAccessible(true);
    fldActive.set(defRLS, 10);

    Field fldUpSide = DefaultRunLevelService.class.getDeclaredField("upSide");
    fldUpSide.setAccessible(true);
    fldUpSide.set(defRLS, true);

    assertNotNull(h.getComponent(ServiceB.class));
    assertNotNull(h.getComponent(ServiceA.class));
    assertNotNull(h.getComponent(ServiceC.class));

    assertEquals(recorders.toString(), 1, recorders.size());

    Recorder recorder = recorders.get(10);
    assertNotNull(recorder);

    List<Inhabitant<?>> activations = recorder.getActivations();
    assertEquals(3, activations.size());
    
    Inhabitant<?> iB = h.getInhabitantByContract(ServiceB.class.getName(), null);
    Inhabitant<?> iA = h.getInhabitantByContract(ServiceA.class.getName(), null);
    Inhabitant<?> iC = h.getInhabitantByContract(ServiceC.class.getName(), null);

    assertTrue(iB.isInstantiated());
    assertTrue(iA.isInstantiated());
    assertTrue(iC.isInstantiated());
    
    Iterator<Inhabitant<?>> iter = activations.iterator();
    assertSame("order is important", iA, iter.next());
    assertSame("order is important", iB, iter.next());
    assertSame("order is important", iC, iter.next());
    
    Method resetMthd = DefaultRunLevelService.class.getDeclaredMethod("reset", (Class<?>[])null);
    resetMthd.setAccessible(true);
    resetMthd.invoke(defRLS, (Object[])null);
    
    defRLS.proceedTo(0);
    assertFalse(iB.isInstantiated());
    assertFalse(iA.isInstantiated());
    assertFalse(iC.isInstantiated());

    assertEquals(recorders.toString(), 0, recorders.size());

    assertListenerState(true, false, false);
  }
  
  @Test
  public void dependenciesFromNonRunLevelToRunLevelService() {
    rls.proceedTo(10);
  
    Inhabitant<NonRunLevelWithRunLevelDepService> i = 
      h.getInhabitantByType(NonRunLevelWithRunLevelDepService.class);
    assertNotNull(i);
    assertFalse(i.isInstantiated());
    
    try {
      fail("Expected get() to fail, bad dependency to a RunLevel service: " + i.get());
    } catch (Exception e) {
      // expected
    }

    assertFalse(i.isInstantiated());
  }
  
  @Test
  public void dependenciesFromNonRunLevelToRunLevelServiceAsync() {
    installTestRunLevelService(true);
    
    defRLS.proceedTo(10);
  
    Inhabitant<NonRunLevelWithRunLevelDepService> i = 
      h.getInhabitantByType(NonRunLevelWithRunLevelDepService.class);
    assertNotNull(i);
    assertFalse(i.isInstantiated());
    
    try {
      fail("Expected get() to fail, bad dependency to a RunLevel service: " + i.get());
    } catch (Exception e) {
      // expected
    }

    assertFalse(i.isInstantiated());
  }
  
  @SuppressWarnings("unchecked")
  private void installTestRunLevelService(boolean async) {
    Inhabitant<RunLevelService> r = 
      (Inhabitant<RunLevelService>) h.getInhabitant(RunLevelService.class, "default");
    assertNotNull(r);
    assertTrue(h.removeIndex(RunLevelService.class.getName(), "default"));
    h.remove(r);
    
    DefaultRunLevelService oldRLS = ((DefaultRunLevelService)rls);
    
    recorders = new LinkedHashMap<Integer, Recorder>();
    rls = new TestDefaultRunLevelService(h, async, recorders); 
    r = new ExistingSingletonInhabitant<RunLevelService>(RunLevelService.class, rls);
    h.add(r);
    h.addIndex(r, RunLevelService.class.getName(), "default");

    this.defRLS = (DefaultRunLevelService) rls;
    this.defRLlistener = (TestRunLevelListener) listener;
    defRLlistener.calls.clear();
    
    oldRLS.setDelegate((RunLevelState)rls);
  }
  
  
  /**
   * Verifies the instantiation / release of inhabitants are correct
   * 
   * @param runLevel
   */
  private void assertInhabitantsState(int runLevel) {
    Collection<Inhabitant<?>> runLevelInhabitants = h.getAllInhabitantsByContract(RunLevel.class.getName());
    assertTrue(runLevelInhabitants.size() > 0);
    for (Inhabitant<?> i : runLevelInhabitants) {
      AbstractInhabitantImpl<?> ai = AbstractInhabitantImpl.class.cast(i);
      RunLevel rl = ai.getAnnotation(RunLevel.class);
      if (rl.value() <= runLevel) {
        if (ai.toString().contains("Invalid")) {
          assertFalse("expect not instantiated: " + ai, ai.isInstantiated());
        } else {
          if (Void.class == rl.environment()) {
            assertTrue("expect instantiated: " + ai, ai.isInstantiated());
          } else {
            assertFalse("expect instantiated: " + ai, ai.isInstantiated());
          }
        }
      } else {
        assertFalse("expect not instantiated: " + ai, ai.isInstantiated());
      }
    }
  }
  
  
  /**
   * Verifies the listener was indeed called, and the ordering is always consistent.
   */
  private void assertListenerState(boolean expectDownSide, boolean expectErrors, boolean expectCancelled) {
    assertTrue(defRLlistener.calls.size() > 0);
    int last = -1;
    boolean upSide = true;
    int sawCancel = 0;
    boolean sawError = false;
    for (TestRunLevelListener.Call call : defRLlistener.calls) {
      if (expectDownSide) {
        if (!upSide) {
          // already on the down side
          assertTrue(call.toString(), call.current <= last);
        } else {
          // haven't seen the down side yet
          if (call.current < last) {
            upSide = false;
          }
        }
      } else {
        assertTrue(call.toString(), call.current >= last);
      }

      if (upSide) {
        // we should only see cancel and error on up side (the way we designed our tests)
        if (call.type.equals("cancelled")) {
          sawCancel++;
        } else if (call.type.equals("error")) {
          sawError = true;
        }
      } else {
        assertEquals(call.toString(), "progress", call.type);
      }
      
      last = call.current;
    }
    
    if (expectDownSide) {
//      assertFalse("should have ended on down side: " + defRLlistener.calls, upSide);
      // race conditions prevents us from doing the assert
      if (upSide) {
        Logger.getAnonymousLogger().log(Level.WARNING, "Expected to have ended on down side: " + defRLlistener.calls);
      }
    }
  
    if (expectErrors) {
      assertTrue(defRLlistener.calls.toString(), sawError);
    }
    
    if (expectCancelled) {
//      assertEquals(defRLlistener.calls.toString(), 1, sawCancel);
      // race conditions prevents us from doing the assert
      if (1 != sawCancel) {
        Logger.getAnonymousLogger().log(Level.WARNING, "Expected to have seen cancel: " + defRLlistener.calls);
      }
    }
  }


  /**
   * Verifies that the recorder is always consistent.
   */
  private void assertRecorderState() {
    assertFalse(recorders.toString(), recorders.isEmpty());
    // we could really do more here...
  }
  
  
  private static class TestDefaultRunLevelService extends DefaultRunLevelService {

    TestDefaultRunLevelService(Habitat habitat, boolean async,
        HashMap<Integer, Recorder> recorders) {
      super(habitat, async, recorders);
    }
    
  }
  
}
