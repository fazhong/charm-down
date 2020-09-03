package com.gluonhq.impl.charm.down.plugins;

import java.util.logging.Logger;
import com.gluonhq.charm.down.plugins.AugmentedRealityService;

public abstract class DefaultAugmentedRealityService implements AugmentedRealityService {

    private static final Logger LOG = Logger.getLogger(DefaultAugmentedRealityService.class.getName());
    protected static boolean debug;
    

    public DefaultAugmentedRealityService() {
        if ("true".equals(System.getProperty(Constants.DOWN_DEBUG))) {
            debug = true;
        }
    }
    
}
