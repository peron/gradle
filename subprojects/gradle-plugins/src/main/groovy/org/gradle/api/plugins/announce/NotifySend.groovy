package org.gradle.api.plugins.announce

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * This class wraps the Ubuntu Notify Send functionality.
 *
 * @author Hackergarten
 */

class NotifySend {

    private static Logger logger = LoggerFactory.getLogger(NotifySend)

    void send(String title, String message) {
        def cmd = [
                'notify-send',
                title,
                message
        ]
        try {
            cmd.execute()
        } catch (java.io.IOException e) {
            logger.error('Could not find notify-send command', e)
        }
    }
}
