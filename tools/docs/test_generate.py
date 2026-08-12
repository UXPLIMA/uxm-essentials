import unittest

from generate import render_commands, render_permissions, render_placeholders, render_settings, rewrite

MODULE = {
    "id": "homes",
    "configPath": "modules/homes/config.conf",
    "enabledByDefault": True,
    "commands": [
        {
            "literal": "home",
            "aliases": ["h"],
            "permission": "uxmessentials.home.use",
            "description": "Open your homes.",
        }
    ],
    "permissions": [
        {
            "node": "uxmessentials.home.use",
            "fallback": "TRUE",
            "shape": "FIXED",
            "description": "Open your homes.",
        }
    ],
    "settings": [{"key": "default-limit", "value": "3", "description": "homes per player"}],
    "placeholders": [
        {"key": "home_count", "scope": "PLAYER", "description": "How many homes | are saved"}
    ],
}


class RenderTest(unittest.TestCase):

    def test_commands_table_lists_the_literal_with_its_aliases(self):
        table = render_commands(MODULE)
        self.assertIn("| `/home` (`/h`) | Open your homes. | `uxmessentials.home.use` |", table)
        self.assertTrue(table.startswith("| Command | What it does | Permission |"))

    def test_a_command_with_no_permission_constant_leaves_the_cell_empty(self):
        module = dict(MODULE, commands=[dict(MODULE["commands"][0], permission="")])
        self.assertIn("| `/home` (`/h`) | Open your homes. |  |", render_commands(module))

    def test_permission_default_is_written_in_words(self):
        self.assertIn(
            "| `uxmessentials.home.use` | everyone | Open your homes. |", render_permissions(MODULE)
        )

    def test_settings_table_carries_the_shipped_default(self):
        self.assertIn("| `default-limit` | `3` | homes per player |", render_settings(MODULE))

    def test_a_pipe_in_a_description_is_escaped(self):
        self.assertIn("How many homes \\| are saved", render_placeholders(MODULE))

    def test_an_angle_bracket_outside_backticks_is_escaped(self):
        module = dict(
            MODULE,
            permissions=[dict(MODULE["permissions"][0], description="/eco give <player> and `<n>` tiers")],
        )
        self.assertIn("/eco give \\<player> and `<n>` tiers", render_permissions(module))


class RewriteTest(unittest.TestCase):

    PAGE = "intro\n\n## Commands\n{/* generated:commands */}\nold\n{/* /generated */}\n\n## Notes\n- a\n"

    def test_replaces_only_what_is_between_the_markers(self):
        out = rewrite(self.PAGE, {"commands": "NEW"})
        self.assertIn("{/* generated:commands */}\nNEW\n{/* /generated */}", out)
        self.assertIn("intro", out)
        self.assertIn("- a", out)

    def test_running_twice_changes_nothing(self):
        once = rewrite(self.PAGE, {"commands": "NEW"})
        self.assertEqual(once, rewrite(once, {"commands": "NEW"}))

    def test_a_marker_with_no_data_is_an_error(self):
        with self.assertRaises(ValueError):
            rewrite(self.PAGE, {})

    def test_data_with_no_marker_is_an_error(self):
        with self.assertRaises(ValueError):
            rewrite(self.PAGE, {"commands": "NEW", "settings": "NEW"})

    def test_fills_a_pair_that_has_nothing_between_it_yet(self):
        page = "intro\n\n## Commands\n{/* generated:commands */}\n{/* /generated */}\n"
        self.assertIn("{/* generated:commands */}\nNEW\n{/* /generated */}", rewrite(page, {"commands": "NEW"}))

    def test_two_empty_pairs_are_filled_independently(self):
        page = (
            "intro\n\n{/* generated:commands */}\n{/* /generated */}\n"
            "\n{/* generated:settings */}\n{/* /generated */}\n"
        )
        out = rewrite(page, {"commands": "ONE", "settings": "TWO"})
        self.assertIn("{/* generated:commands */}\nONE\n{/* /generated */}", out)
        self.assertIn("{/* generated:settings */}\nTWO\n{/* /generated */}", out)

    def test_a_duplicated_marker_is_an_error(self):
        with self.assertRaises(ValueError):
            rewrite(self.PAGE + self.PAGE, {"commands": "NEW"})


if __name__ == "__main__":
    unittest.main()
