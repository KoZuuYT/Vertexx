// Vertexx Server — janela de mundos e configurações do servidor de teste.
// Compilado junto com ServerLauncher.cs por tools\build-launcher.ps1 (C# 5, csc do Windows).
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Text;
using System.Windows.Forms;

/// <summary>Leitura e gravação do server.properties (formato java.util.Properties) e das opções do aplicativo.</summary>
internal static class ServerConfig
{
    public static string Escape(string value)
    {
        var sb = new StringBuilder();
        foreach (char c in value)
        {
            if (c == '\\') sb.Append("\\\\");
            else if (c == ':' || c == '=' || c == '#' || c == '!') sb.Append('\\').Append(c);
            else if (c > 126) sb.Append("\\u").Append(((int)c).ToString("x4"));
            else sb.Append(c);
        }
        return sb.ToString();
    }

    public static string Unescape(string value)
    {
        var sb = new StringBuilder();
        for (int i = 0; i < value.Length; i++)
        {
            char c = value[i];
            if (c == '\\' && i + 1 < value.Length)
            {
                char n = value[++i];
                if (n == 'u' && i + 4 < value.Length)
                {
                    int code;
                    if (int.TryParse(value.Substring(i + 1, 4), System.Globalization.NumberStyles.HexNumber, null, out code))
                    {
                        sb.Append((char)code);
                        i += 4;
                        continue;
                    }
                }
                sb.Append(n);
            }
            else sb.Append(c);
        }
        return sb.ToString();
    }

    public static Dictionary<string, string> Read(string path)
    {
        var map = new Dictionary<string, string>();
        if (!File.Exists(path)) return map;
        foreach (string line in MainForm.ReadLinesShared(path))
        {
            int eq = line.IndexOf('=');
            if (line.StartsWith("#") || eq < 0) continue;
            map[line.Substring(0, eq).Trim()] = Unescape(line.Substring(eq + 1).Trim());
        }
        return map;
    }

    /// <summary>Atualiza (ou acrescenta) as chaves informadas, preservando o resto do arquivo.</summary>
    public static void Write(string path, Dictionary<string, string> changes)
    {
        var lines = File.Exists(path) ? MainForm.ReadLinesShared(path) : new List<string>();
        foreach (var pair in changes)
        {
            string line = pair.Key + "=" + Escape(pair.Value);
            int index = lines.FindIndex(l => l.StartsWith(pair.Key + "="));
            if (index >= 0) lines[index] = line;
            else lines.Add(line);
        }
        File.WriteAllLines(path, lines, new UTF8Encoding(false));
    }

    public static string ServerProps(string runDir) { return Path.Combine(runDir, "server.properties"); }
    public static string LauncherProps(string runDir) { return Path.Combine(runDir, "vertexx-launcher.properties"); }

    public static int MemoryMb(string runDir)
    {
        string value;
        int mb;
        if (Read(LauncherProps(runDir)).TryGetValue("memory-mb", out value) && int.TryParse(value, out mb) && mb >= 512)
            return mb;
        return 2048;
    }

    /// <summary>Arquivo extra do log4j que silencia ruído inofensivo (contadores de desempenho do Windows).</summary>
    public static void EnsureLogConfig(string runDir)
    {
        string path = Path.Combine(runDir, "vertexx-log4j2.xml");
        if (File.Exists(path)) return;
        File.WriteAllText(path,
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
            "<!-- Silencia ruido conhecido e inofensivo: contadores de desempenho corrompidos do Windows (oshi). -->\r\n" +
            "<Configuration status=\"WARN\">\r\n  <Loggers>\r\n" +
            "    <Logger name=\"oshi\" level=\"off\"/>\r\n" +
            "    <Logger name=\"oshi.driver.windows.registry.HkeyPerformanceDataUtil\" level=\"off\"/>\r\n" +
            "    <Logger name=\"oshi.util.platform.windows.PerfCounterQuery\" level=\"off\"/>\r\n" +
            "    <Logger name=\"oshi.util.platform.windows.PerfCounterWildcardQuery\" level=\"off\"/>\r\n" +
            "  </Loggers>\r\n</Configuration>\r\n", new UTF8Encoding(false));
    }

    public static string WorldName(string runDir)
    {
        string name;
        return Read(ServerProps(runDir)).TryGetValue("level-name", out name) && name.Length > 0 ? name : "world";
    }
}

internal sealed class SettingsForm : Form
{
    private static readonly Color Bg = Color.FromArgb(24, 22, 34);
    private static readonly Color Panel = Color.FromArgb(36, 33, 52);
    private static readonly Color Accent = Color.FromArgb(127, 90, 240);
    private static readonly Color Danger = Color.FromArgb(160, 50, 60);

    private static readonly string[] WorldTypes = { "Normal", "Plano", "Vazio (void)", "Biomas grandes", "Ampliado" };
    private static readonly string[] Modes = { "Sobrevivência", "Criativo", "Aventura", "Espectador" };
    private static readonly string[] ModeIds = { "survival", "creative", "adventure", "spectator" };
    private static readonly string[] Difficulties = { "Pacífico", "Fácil", "Normal", "Difícil" };
    private static readonly string[] DifficultyIds = { "peaceful", "easy", "normal", "hard" };
    private const string VoidSettings =
        "{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:the_void\",\"structure_overrides\":[]}";

    private readonly string runDir;
    private readonly bool stopped;

    private readonly Panel worldsPanel = new Panel();
    private readonly Panel serverPanel = new Panel();
    private readonly Button tabWorlds = new Button();
    private readonly Button tabServer = new Button();
    private readonly Label note = new Label();

    // mundos
    private readonly Label current = new Label();
    private readonly ListBox worlds = new ListBox();
    private readonly TextBox newName = new TextBox();
    private readonly ComboBox newType = new ComboBox();
    private readonly TextBox newSeed = new TextBox();
    private readonly CheckBox newStructures = new CheckBox();
    private readonly ComboBox newMode = new ComboBox();
    private readonly ComboBox newDifficulty = new ComboBox();

    // servidor
    private readonly TextBox motd = new TextBox();
    private readonly NumericUpDown port = new NumericUpDown();
    private readonly NumericUpDown slots = new NumericUpDown();
    private readonly NumericUpDown view = new NumericUpDown();
    private readonly NumericUpDown memory = new NumericUpDown();
    private readonly CheckBox online = new CheckBox();
    private readonly CheckBox whitelist = new CheckBox();
    private readonly CheckBox pvp = new CheckBox();
    private readonly ComboBox mode = new ComboBox();
    private readonly ComboBox difficulty = new ComboBox();

    public SettingsForm(string runDir, bool stopped)
    {
        this.runDir = runDir;
        this.stopped = stopped;

        Text = "Mundos e servidor";
        ClientSize = new Size(560, 560);
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        StartPosition = FormStartPosition.CenterParent;
        BackColor = Bg;
        ForeColor = Color.White;
        Font = new Font("Segoe UI", 9.5f);

        Tab(tabWorlds, "Mundos", 16, delegate { Show(true); });
        Tab(tabServer, "Servidor", 136, delegate { Show(false); });

        note.SetBounds(16, 520, 528, 32);
        note.ForeColor = stopped ? Color.Silver : Color.Orange;
        note.Text = stopped ? "As mudanças valem na próxima vez que o servidor iniciar."
            : "O servidor está ligado: pare-o para alterar mundos e configurações.";

        foreach (Panel p in new[] { worldsPanel, serverPanel })
        {
            p.SetBounds(16, 58, 528, 456);
            p.BackColor = Bg;
            Controls.Add(p);
        }
        Controls.AddRange(new Control[] { tabWorlds, tabServer, note });

        BuildWorlds();
        BuildServer();
        LoadServer();
        RefreshWorlds();
        Show(true);
    }

    // ── Auxiliares de interface ────────────────────────────────

    private static void StyleButton(Button b, Color color)
    {
        b.FlatStyle = FlatStyle.Flat;
        b.FlatAppearance.BorderSize = 0;
        b.BackColor = color;
        b.ForeColor = Color.White;
        b.Cursor = Cursors.Hand;
    }

    private void Tab(Button b, string text, int x, EventHandler click)
    {
        StyleButton(b, Panel);
        b.Text = text;
        b.SetBounds(x, 14, 112, 32);
        b.Click += click;
    }

    private void Show(bool worldsTab)
    {
        worldsPanel.Visible = worldsTab;
        serverPanel.Visible = !worldsTab;
        tabWorlds.BackColor = worldsTab ? Accent : Panel;
        tabServer.BackColor = worldsTab ? Panel : Accent;
    }

    private static Label Lbl(Control parent, string text, int x, int y, int w)
    {
        var l = new Label { Text = text, ForeColor = Color.Silver, AutoSize = false };
        l.SetBounds(x, y, w, 20);
        parent.Controls.Add(l);
        return l;
    }

    private static void Field(Control parent, Control c, int x, int y, int w)
    {
        c.SetBounds(x, y, w, 26);
        c.BackColor = Panel;
        c.ForeColor = Color.White;
        var tb = c as TextBox;
        if (tb != null) tb.BorderStyle = BorderStyle.FixedSingle;
        var cb = c as ComboBox;
        if (cb != null) { cb.DropDownStyle = ComboBoxStyle.DropDownList; cb.FlatStyle = FlatStyle.Flat; }
        parent.Controls.Add(c);
    }

    private static void Check(Control parent, CheckBox c, string text, int x, int y, int w)
    {
        c.Text = text;
        c.ForeColor = Color.White;
        c.SetBounds(x, y, w, 24);
        parent.Controls.Add(c);
    }

    private static void Numeric(Control parent, NumericUpDown n, int min, int max, int x, int y, int w)
    {
        n.Minimum = min;
        n.Maximum = max;
        Field(parent, n, x, y, w);
    }

    // ── Aba de mundos ──────────────────────────────────────────

    private void BuildWorlds()
    {
        Control p = worldsPanel;
        current.SetBounds(0, 0, 528, 22);
        current.Font = new Font("Segoe UI Semibold", 10.5f);
        p.Controls.Add(current);

        worlds.SetBounds(0, 26, 528, 110);
        worlds.BackColor = Panel;
        worlds.ForeColor = Color.White;
        worlds.BorderStyle = BorderStyle.FixedSingle;
        p.Controls.Add(worlds);

        var use = new Button { Text = "Usar o selecionado" };
        StyleButton(use, Accent);
        use.SetBounds(0, 142, 170, 30);
        use.Enabled = stopped;
        use.Click += delegate { UseSelected(); };
        var del = new Button { Text = "Excluir" };
        StyleButton(del, Danger);
        del.SetBounds(176, 142, 90, 30);
        del.Enabled = stopped;
        del.Click += delegate { DeleteSelected(); };
        var open = new Button { Text = "Abrir pasta" };
        StyleButton(open, Panel);
        open.SetBounds(272, 142, 110, 30);
        open.Click += delegate { Process.Start("explorer.exe", "\"" + runDir + "\""); };
        p.Controls.AddRange(new Control[] { use, del, open });

        var title = new Label { Text = "Criar um novo mundo", Font = new Font("Segoe UI Semibold", 10.5f) };
        title.SetBounds(0, 190, 528, 22);
        p.Controls.Add(title);

        Lbl(p, "Nome do mundo", 0, 220, 250);
        Field(p, newName, 0, 242, 250);
        newName.Text = "mundo_teste";
        Lbl(p, "Tipo", 270, 220, 250);
        Field(p, newType, 270, 242, 258);
        newType.Items.AddRange(WorldTypes);
        newType.SelectedIndex = 1;

        Lbl(p, "Seed (opcional)", 0, 280, 250);
        Field(p, newSeed, 0, 302, 250);
        Lbl(p, "Modo de jogo", 270, 280, 120);
        Field(p, newMode, 270, 302, 125);
        newMode.Items.AddRange(Modes);
        newMode.SelectedIndex = 0;
        Lbl(p, "Dificuldade", 403, 280, 120);
        Field(p, newDifficulty, 403, 302, 125);
        newDifficulty.Items.AddRange(Difficulties);
        newDifficulty.SelectedIndex = 1;

        Check(p, newStructures, "Gerar estruturas (vilas, fortalezas...)", 0, 340, 330);
        newStructures.Checked = true;

        var create = new Button { Text = "Criar e usar" };
        StyleButton(create, Accent);
        create.SetBounds(0, 376, 170, 34);
        create.Enabled = stopped;
        create.Click += delegate { CreateWorld(); };
        p.Controls.Add(create);

        var hint = new Label
        {
            Text = "O mundo é gerado quando o servidor inicia. Nether e End ficam dentro da mesma pasta.",
            ForeColor = Color.Silver
        };
        hint.SetBounds(0, 418, 528, 36);
        p.Controls.Add(hint);
    }

    private void RefreshWorlds()
    {
        string active = ServerConfig.WorldName(runDir);
        var type = ServerConfig.Read(ServerConfig.ServerProps(runDir));
        string levelType;
        type.TryGetValue("level-type", out levelType);
        current.Text = "Mundo atual: " + active + (string.IsNullOrEmpty(levelType) ? "" : "  (" + levelType.Replace("minecraft:", "") + ")");
        worlds.Items.Clear();
        foreach (string dir in Directory.GetDirectories(runDir).OrderBy(d => d))
        {
            if (!File.Exists(Path.Combine(dir, "level.dat"))) continue;
            string name = Path.GetFileName(dir);
            worlds.Items.Add(name == active ? name + "   ●  em uso" : name);
        }
        if (worlds.Items.Count > 0) worlds.SelectedIndex = 0;
    }

    private string SelectedWorld()
    {
        if (worlds.SelectedItem == null) return null;
        string text = worlds.SelectedItem.ToString();
        int mark = text.IndexOf("   ●");
        return mark >= 0 ? text.Substring(0, mark) : text;
    }

    private void UseSelected()
    {
        string name = SelectedWorld();
        if (name == null) return;
        var changes = new Dictionary<string, string>();
        changes["level-name"] = name;
        ServerConfig.Write(ServerConfig.ServerProps(runDir), changes);
        RefreshWorlds();
        LoadServer();
        MessageBox.Show("O mundo \"" + name + "\" será usado na próxima inicialização.", "Vertexx Server");
    }

    private void DeleteSelected()
    {
        string name = SelectedWorld();
        if (name == null) return;
        if (name == ServerConfig.WorldName(runDir))
        {
            MessageBox.Show("Esse é o mundo em uso. Escolha outro mundo antes de excluí-lo.", "Vertexx Server");
            return;
        }
        if (MessageBox.Show("Excluir o mundo \"" + name + "\" para sempre?\nIsso não pode ser desfeito.", "Vertexx Server",
                MessageBoxButtons.YesNo, MessageBoxIcon.Warning) != DialogResult.Yes) return;
        try { Directory.Delete(Path.Combine(runDir, name), true); }
        catch (Exception ex) { MessageBox.Show("Não foi possível excluir: " + ex.Message, "Vertexx Server"); }
        RefreshWorlds();
    }

    private static string Sanitize(string text)
    {
        var sb = new StringBuilder();
        foreach (char c in text.Trim())
        {
            if (char.IsLetterOrDigit(c) && c < 128 || c == '_' || c == '-') sb.Append(c);
            else if (c == ' ') sb.Append('_');
        }
        return sb.ToString();
    }

    private void CreateWorld()
    {
        string name = Sanitize(newName.Text);
        if (name.Length == 0)
        {
            MessageBox.Show("Informe um nome (letras, números, _ ou -).", "Vertexx Server");
            return;
        }
        string[] reserved = { "plugins", "logs", "libraries", "versions", "cache", "config", "crash-reports" };
        if (reserved.Contains(name.ToLowerInvariant()))
        {
            MessageBox.Show("Esse nome é reservado pelo servidor. Escolha outro.", "Vertexx Server");
            return;
        }
        if (File.Exists(Path.Combine(runDir, name, "level.dat")))
        {
            MessageBox.Show("Já existe um mundo com esse nome. Selecione-o na lista e use \"Usar o selecionado\".", "Vertexx Server");
            return;
        }
        string type;
        string settings = "{}";
        switch (newType.SelectedIndex)
        {
            case 0: type = "minecraft:normal"; break;
            case 1: type = "minecraft:flat"; break;
            case 2: type = "minecraft:flat"; settings = VoidSettings; break;
            case 3: type = "minecraft:large_biomes"; break;
            default: type = "minecraft:amplified"; break;
        }
        var changes = new Dictionary<string, string>();
        changes["level-name"] = name;
        changes["level-type"] = type;
        changes["generator-settings"] = settings;
        changes["level-seed"] = newSeed.Text.Trim();
        changes["generate-structures"] = newStructures.Checked ? "true" : "false";
        changes["gamemode"] = ModeIds[newMode.SelectedIndex];
        changes["difficulty"] = DifficultyIds[newDifficulty.SelectedIndex];
        ServerConfig.Write(ServerConfig.ServerProps(runDir), changes);
        LoadServer();
        MessageBox.Show("Mundo \"" + name + "\" (" + WorldTypes[newType.SelectedIndex] + ") definido.\n" +
            "Ele será gerado quando você iniciar o servidor.", "Vertexx Server");
        DialogResult = DialogResult.OK;
        Close();
    }

    // ── Aba do servidor ────────────────────────────────────────

    private void BuildServer()
    {
        Control p = serverPanel;
        Lbl(p, "Nome do servidor (aparece na lista de servidores)", 0, 0, 528);
        Field(p, motd, 0, 22, 528);

        Lbl(p, "Porta", 0, 64, 120);
        Numeric(p, port, 1024, 65535, 0, 86, 120);
        Lbl(p, "Máx. de jogadores", 140, 64, 130);
        Numeric(p, slots, 1, 500, 140, 86, 120);
        Lbl(p, "Distância de visão", 280, 64, 130);
        Numeric(p, view, 2, 32, 280, 86, 120);
        Lbl(p, "Memória (MB)", 420, 64, 108);
        Numeric(p, memory, 512, 32768, 420, 86, 108);
        memory.Increment = 256;

        Lbl(p, "Modo de jogo padrão", 0, 130, 200);
        Field(p, mode, 0, 152, 200);
        mode.Items.AddRange(Modes);
        Lbl(p, "Dificuldade", 220, 130, 200);
        Field(p, difficulty, 220, 152, 200);
        difficulty.Items.AddRange(Difficulties);

        Check(p, online, "Modo online (exige conta original do Minecraft)", 0, 200, 420);
        Check(p, whitelist, "Lista de permissão (whitelist) ligada", 0, 228, 420);
        Check(p, pvp, "Jogador contra jogador (PvP)", 0, 256, 420);

        var save = new Button { Text = "Salvar" };
        StyleButton(save, Accent);
        save.SetBounds(0, 304, 140, 34);
        save.Enabled = stopped;
        save.Click += delegate { SaveServer(); };
        p.Controls.Add(save);

        var hint = new Label
        {
            Text = "Dica: o servidor de teste roda em modo offline. Com o modo online ligado, só entram contas originais.",
            ForeColor = Color.Silver
        };
        hint.SetBounds(0, 350, 528, 40);
        p.Controls.Add(hint);
    }

    private static int Num(Dictionary<string, string> map, string key, int fallback)
    {
        string value;
        int n;
        return map.TryGetValue(key, out value) && int.TryParse(value, out n) ? n : fallback;
    }

    private static bool Flag(Dictionary<string, string> map, string key, bool fallback)
    {
        string value;
        return map.TryGetValue(key, out value) ? value == "true" : fallback;
    }

    private static int IndexOf(string[] ids, string value, int fallback)
    {
        int i = Array.IndexOf(ids, value);
        return i >= 0 ? i : fallback;
    }

    private void LoadServer()
    {
        var map = ServerConfig.Read(ServerConfig.ServerProps(runDir));
        string text;
        motd.Text = map.TryGetValue("motd", out text) ? text : "Vertexx teste";
        port.Value = Math.Max(port.Minimum, Math.Min(port.Maximum, Num(map, "server-port", 25565)));
        slots.Value = Math.Max(slots.Minimum, Math.Min(slots.Maximum, Num(map, "max-players", 20)));
        view.Value = Math.Max(view.Minimum, Math.Min(view.Maximum, Num(map, "view-distance", 10)));
        memory.Value = Math.Max(memory.Minimum, Math.Min(memory.Maximum, ServerConfig.MemoryMb(runDir)));
        online.Checked = Flag(map, "online-mode", true);
        whitelist.Checked = Flag(map, "white-list", false);
        pvp.Checked = Flag(map, "pvp", true);
        string value;
        mode.SelectedIndex = IndexOf(ModeIds, map.TryGetValue("gamemode", out value) ? value : "survival", 0);
        difficulty.SelectedIndex = IndexOf(DifficultyIds, map.TryGetValue("difficulty", out value) ? value : "easy", 1);
    }

    private void SaveServer()
    {
        var changes = new Dictionary<string, string>();
        changes["motd"] = motd.Text.Trim();
        changes["server-port"] = ((int)port.Value).ToString();
        changes["max-players"] = ((int)slots.Value).ToString();
        changes["view-distance"] = ((int)view.Value).ToString();
        changes["online-mode"] = online.Checked ? "true" : "false";
        changes["white-list"] = whitelist.Checked ? "true" : "false";
        changes["pvp"] = pvp.Checked ? "true" : "false";
        changes["gamemode"] = ModeIds[mode.SelectedIndex];
        changes["difficulty"] = DifficultyIds[difficulty.SelectedIndex];
        ServerConfig.Write(ServerConfig.ServerProps(runDir), changes);
        var launcher = new Dictionary<string, string>();
        launcher["memory-mb"] = ((int)memory.Value).ToString();
        ServerConfig.Write(ServerConfig.LauncherProps(runDir), launcher);
        MessageBox.Show("Configurações salvas.", "Vertexx Server");
        DialogResult = DialogResult.OK;
        Close();
    }
}
