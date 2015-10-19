package com.prismtech.vortex.demos.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Application;
import play.GlobalSettings;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class Global extends GlobalSettings {

    private static Logger LOG = LoggerFactory.getLogger(Global.class);

    public Global() {
    }

    @Override
    public void beforeStart(Application application) {
        super.beforeStart(application);
        System.setProperty("ddsi.network.interface", "en0");
        System.setProperty("ddsi.discovery.subscribeCMTopics", "true");
//        System.setProperty("dds.types.classpath", "/Users/tmcclean/Projects/vortex-demo/cafe/duke-osgi-idl/target/duke-osgi-idl-1.0.0.jar");
        loadTypesFromProperties();
    }

    private void loadTypesFromProperties() {
        final String[] classpaths = System.getProperty("dds.types.classpath", "").split(":");
        List<File> files = new ArrayList<File>();

        for (String cp : classpaths) {
            if (cp != null && !cp.isEmpty()) {
                final File f = new File(cp);
                if (f.exists()) {
                    files.add(f);
                } else {
                    LOG.warn("Path {} does not exist !", cp);
                }
            }
        }

        loadClassPaths(files.toArray(new File[files.size()]));
    }

    private void loadClassPaths(final File[] classpaths) {
        Method method = null;
        try {
            method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        } catch (NoSuchMethodException e) {
            LOG.error("Unable to access the URLClassLoader#addURL method. No class paths will be loaded.", e);
        }

        if (method != null) {
            method.setAccessible(true);
            for (File cp : classpaths) {
                try {
                    final URL url = cp.toURI().toURL();
                    method.invoke(this.getClass().getClassLoader(), url);

                    // loading the data types into the parent ClassLoader only in dev mode
                    if (play.Play.isDev()) {
                        method.invoke(this.getClass().getClassLoader().getParent(), url);
                    }

                    LOG.info("{} loaded", url.toString());
                } catch (MalformedURLException e) {
                    LOG.error("Error loading classpath {}.", cp.getAbsolutePath());
                    LOG.error("Error loading classpath. [exception]", e);
                } catch (IllegalAccessException e) {
                    LOG.error("Error loading classpath {}.", cp.getAbsolutePath());
                    LOG.error("Error loading classpath. [exception]", e);
                } catch (InvocationTargetException e) {
                    LOG.error("Error loading classpath {}.", cp.getAbsolutePath());
                    LOG.error("Error loading classpath. [exception]", e);
                }
            }
        }
    }
}
