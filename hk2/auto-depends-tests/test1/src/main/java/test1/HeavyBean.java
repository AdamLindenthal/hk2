package test1;

import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.Singleton;

/**
 * @author Jerome Dochez
 */
@Service
@Scoped(Singleton.class)
public class HeavyBean {
    
}
