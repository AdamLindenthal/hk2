
package org.jvnet.hk2.config.test;

import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.InjectionTarget;
import org.jvnet.hk2.config.NoopConfigInjector;

@Service(name = "generic-container", metadata = "target=org.jvnet.hk2.config.test.GenericContainer,@max-pool-size=optional,@max-pool-size=default:32,@max-pool-size=datatype:java.lang.String,@max-pool-size=leaf,@startup-time=optional,@startup-time=default:1234,@startup-time=datatype:java.lang.String,@startup-time=leaf,@int-value=optional,@int-value=default:1234,@int-value=datatype:java.lang.String,@int-value=leaf")
@InjectionTarget(GenericContainer.class)
public class GenericContainerInjector
    extends NoopConfigInjector
{


}
