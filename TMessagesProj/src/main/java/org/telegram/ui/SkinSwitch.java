package org.telegram.ui;

/**
 * Which screens draw with the skin. Each screen switches on its own; one that
 * is off keeps Telegram's and Mango's own interface, untouched.
 */
public final class SkinSwitch {
    /** The tab screen: the aluminium dock and its pages, 我 among them, in place of the glass bar. */
    public static final boolean MAIN_TABS = true;

    private SkinSwitch() {
    }
}
