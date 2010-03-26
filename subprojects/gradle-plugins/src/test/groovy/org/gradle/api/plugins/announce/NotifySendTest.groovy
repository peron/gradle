package org.gradle.api.plugins.announce

class NotifySendTest extends GroovyTestCase {

    static class ExceptionCategory {
        static void execute(List list) {
            throw new IOException()
        }
    }

    static class MockCategory {
        static def capture
        static void execute(List list) {
            capture = list
        }
    }

    public void testWithException() {
        use(ExceptionCategory) {
            def notifier = new NotifySend()
            notifier.send("title", "body")
        }
    }

    public void testCanSendMessage() {

        use(MockCategory) {
            def notifier = new NotifySend()
            notifier.send("title", "body")
            assert ['notify-send', 'title', 'body'] == MockCategory.capture, "nothing was executed"
        }
    }

    public void testIntegrationTest() {
        def notifier = new NotifySend()
        notifier.send("title", "body")
    }
}

