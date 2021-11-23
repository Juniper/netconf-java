package net.juniper.netconf;

import net.juniper.netconf.element.Datastore;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class DatastoreTest {
    @Test
    public void testDatastoreName() {
        assertThat(Datastore.OPERATIONAL.toString(), is("operational"));
        assertThat(Datastore.RUNNING.toString(), is("running"));
        assertThat(Datastore.CANDIDATE.toString(), is("candidate"));
        assertThat(Datastore.STARTUP.toString(), is("startup"));
        assertThat(Datastore.INTENDED.toString(), is("intended"));
    }
}
