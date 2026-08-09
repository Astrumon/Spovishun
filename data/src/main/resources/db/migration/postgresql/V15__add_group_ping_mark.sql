-- The emoji `/ping` repeats once per member becomes per-group and hideable (spovishun-180).
--
-- Two columns rather than one nullable column, because the setting has three states and only two of
-- them are a value: NULL means "fall back to the locale default", a string means "this emoji", and
-- `ping_mark_enabled = FALSE` means "render nothing". Keeping the flag separate is what lets a group
-- be hidden without forgetting the emoji it had — turning the mark back on restores it.
ALTER TABLE group_settings
    ADD COLUMN ping_mark         VARCHAR(32),
    ADD COLUMN ping_mark_enabled BOOLEAN NOT NULL DEFAULT TRUE;
