package org.jvnet.hk2.test.impl;

import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.scopes.PerLookup;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.Inhabitant;
import org.jvnet.hk2.component.InhabitantRequested;

@Service
@Scoped(PerLookup.class)
public class PerLookupServiceNested2 implements PostConstruct, PreDestroy, InhabitantRequested {
    public static int constructs;
    public static int destroys;
    
    public Inhabitant<?> self;
    
    @Inject
    private PerLookupServiceNested3 perLookupServiceNested3;

    @Override
    public void postConstruct() {
      constructs++;
      if (null == self) {
        throw new IllegalStateException();
      }
      if (null == perLookupServiceNested3) {
        throw new IllegalStateException();
      }
    }

    @Override
    public void preDestroy() {
      destroys++;
      if (null == self) {
        throw new IllegalStateException();
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setInhabitant(Inhabitant inhabitant) {
      self = inhabitant;
    }
}
