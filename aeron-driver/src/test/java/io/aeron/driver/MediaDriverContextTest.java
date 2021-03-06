package io.aeron.driver;

import io.aeron.driver.MediaDriver.Context;
import io.aeron.driver.buffer.LogFactory;
import org.agrona.ErrorHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

import java.io.IOError;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import static io.aeron.driver.Configuration.NAK_MAX_BACKOFF_DEFAULT_NS;
import static io.aeron.driver.Configuration.NAK_MULTICAST_MAX_BACKOFF_PROP_NAME;
import static io.aeron.test.Tests.throwOnClose;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MediaDriverContextTest
{
    @Test
    public void nakMulticastMaxBackoffNsDefaultValue()
    {
        final Context context = new Context();
        assertEquals(NAK_MAX_BACKOFF_DEFAULT_NS, context.nakMulticastMaxBackoffNs());
    }

    @Test
    public void nakMulticastMaxBackoffNsValueFromSystemProperty()
    {
        System.setProperty(NAK_MULTICAST_MAX_BACKOFF_PROP_NAME, "333");
        try
        {
            final Context context = new Context();
            assertEquals(333, context.nakMulticastMaxBackoffNs());
        }
        finally
        {
            System.clearProperty(NAK_MULTICAST_MAX_BACKOFF_PROP_NAME);
        }
    }

    @Test
    public void nakMulticastMaxBackoffNsExplicitValue()
    {
        final Context context = new Context();
        context.nakMulticastMaxBackoffNs(Long.MIN_VALUE);
        assertEquals(Long.MIN_VALUE, context.nakMulticastMaxBackoffNs());
    }

    @Test
    void closeErrorHandling(final @TempDir Path tempDir) throws Exception
    {
        final ErrorHandler errorHandler = mock(ErrorHandler.class, withSettings().extraInterfaces(AutoCloseable.class));
        throwOnClose((AutoCloseable)errorHandler, new UncheckedIOException(new IOException("You won't see me!")));

        final LogFactory logFactory = mock(LogFactory.class);
        final IOError logFactoryException = new IOError(new IncompatibleClassChangeError("logging failed"));
        throwOnClose(logFactory, logFactoryException);

        final Context context = new Context()
            .errorHandler(errorHandler)
            .logFactory(logFactory)
            .dirDeleteOnShutdown(true)
            .aeronDirectoryName(tempDir.toAbsolutePath().resolve("test-aerondir").toString());
        context.concludeAeronDirectory();

        context.close();

        assertNotNull(context.aeronDirectory());
        assertFalse(context.aeronDirectory().exists());
        final InOrder inOrder = inOrder(errorHandler);
        inOrder.verify(errorHandler).onError(logFactoryException);
        inOrder.verify((AutoCloseable)errorHandler).close();
        inOrder.verifyNoMoreInteractions();
    }
}