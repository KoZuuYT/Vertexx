// Vertexx Server — aplicativo leve para ligar e desligar o servidor Paper de teste.
// Compile com tools\build-launcher.ps1 (usa o csc.exe que já vem com o Windows).
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Management;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Windows.Forms;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        // Nada de janela de "erro do .NET Framework": excecoes nao tratadas so vao para o arquivo de log.
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (s, e) => Crash(e.Exception);
        AppDomain.CurrentDomain.UnhandledException += (s, e) => Crash(e.ExceptionObject as Exception);
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new MainForm());
    }

    private static void Crash(Exception ex)
    {
        try
        {
            File.AppendAllText(Path.Combine(Path.GetTempPath(), "VertexxServer-erros.log"),
                DateTime.Now.ToString("s") + " " + (ex == null ? "?" : ex.ToString()) + Environment.NewLine);
        }
        catch { }
    }
}

internal sealed class MainForm : Form
{
    private enum State { Stopped, Starting, Online, Stopping }

    private static readonly Color Bg = Color.FromArgb(24, 22, 34);
    private static readonly Color Panel = Color.FromArgb(36, 33, 52);
    private static readonly Color Accent = Color.FromArgb(127, 90, 240);
    private static readonly Color Green = Color.FromArgb(44, 182, 125);

    private readonly string root;
    private readonly string runDir;
    private readonly Label dot = new Label();
    private readonly Label status = new Label();
    private readonly Label address = new Label();
    private readonly Button toggle = new Button();
    private readonly RichTextBox log = new RichTextBox();
    private readonly TextBox command = new TextBox();
    private readonly Button send = new Button();
    private readonly Button folder = new Button();
    private readonly Button force = new Button();
    private readonly Button settings = new Button();
    private readonly Button restart = new Button();
    private readonly Button files = new Button();
    private readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer();

    private Process launched;
    private State state = State.Stopped;
    private DateTime startedAt;
    private DateTime stoppingAt;
    private DateTime lastScan = DateTime.MinValue;
    private bool externalAlive;
    private long logPos = -1;
    private long errPos = -1;
    private bool restartPending;

    public MainForm()
    {
        runDir = FindRunDir(AppDomain.CurrentDomain.BaseDirectory);
        root = runDir == null ? null : Path.GetDirectoryName(runDir);

        Text = "Vertexx Server";
        ClientSize = new Size(600, 476);
        MinimumSize = new Size(560, 400);
        BackColor = Bg;
        ForeColor = Color.White;
        Font = new Font("Segoe UI", 9.5f);
        StartPosition = FormStartPosition.CenterScreen;

        dot.Text = "●";
        dot.Font = new Font("Segoe UI", 22f);
        dot.SetBounds(16, 10, 36, 44);
        status.Font = new Font("Segoe UI Semibold", 13f);
        status.SetBounds(54, 12, 220, 26);
        address.ForeColor = Color.Silver;
        address.Cursor = Cursors.Hand;
        address.SetBounds(56, 38, 340, 20);
        address.Click += (s, e) => { try { Clipboard.SetText(Address()); address.Text = "Endereço copiado!"; } catch { } };

        Style(toggle, Accent);
        toggle.Font = new Font("Segoe UI Semibold", 11f);
        toggle.SetBounds(400, 14, 184, 44);
        toggle.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        toggle.Click += (s, e) => Toggle();

        Style(restart, Panel);
        restart.Text = "Reiniciar";
        restart.SetBounds(16, 68, 100, 30);
        restart.Click += (s, e) => Restart();

        Style(files, Panel);
        files.Text = "Arquivos";
        files.SetBounds(122, 68, 100, 30);
        files.Click += (s, e) => OpenFiles();

        Style(settings, Panel);
        settings.Text = "Mundos e servidor";
        settings.SetBounds(228, 68, 140, 30);
        settings.Click += (s, e) => OpenSettings();

        log.Multiline = true;
        log.ReadOnly = true;
        log.ScrollBars = RichTextBoxScrollBars.Vertical;
        log.DetectUrls = false;
        log.WordWrap = true;
        log.BackColor = Color.FromArgb(14, 13, 20);
        log.ForeColor = Color.FromArgb(200, 200, 215);
        log.BorderStyle = BorderStyle.None;
        log.Font = new Font("Consolas", 9f);
        log.SetBounds(16, 106, 568, 262);
        log.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;

        command.BackColor = Panel;
        command.ForeColor = Color.White;
        command.BorderStyle = BorderStyle.FixedSingle;
        command.Font = new Font("Consolas", 10f);
        command.SetBounds(16, 416, 380, 26);
        command.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        command.KeyDown += (s, e) =>
        {
            if (e.KeyCode == Keys.Enter) { e.SuppressKeyPress = true; SendCommand(); }
        };

        Style(send, Panel);
        send.Text = "Enviar comando";
        send.SetBounds(404, 415, 110, 28);
        send.Anchor = AnchorStyles.Bottom | AnchorStyles.Right;
        send.Click += (s, e) => SendCommand();

        Style(folder, Panel);
        folder.Text = "Pasta";
        folder.SetBounds(520, 415, 64, 28);
        folder.Anchor = AnchorStyles.Bottom | AnchorStyles.Right;
        folder.Click += (s, e) => { if (runDir != null) Process.Start("explorer.exe", "\"" + runDir + "\""); };

        Style(force, Color.FromArgb(160, 50, 60));
        force.Text = "Forçar encerramento";
        force.SetBounds(16, 448, 160, 24);
        force.Anchor = AnchorStyles.Bottom | AnchorStyles.Left;
        force.Visible = false;
        force.Click += (s, e) => KillServer();

        Controls.AddRange(new Control[] { dot, status, address, toggle, restart, files, settings, log, command, send, folder, force });
        foreach (Control c in new Control[] { dot, status, address }) c.BackColor = Color.Transparent;

        FormClosing += OnClosing;
        timer.Interval = 1000;
        timer.Tick += (s, e) => Tick();
        timer.Start();

        if (runDir == null)
        {
            Append("Não encontrei a pasta 'run' com o paper.jar ao lado deste aplicativo.");
            toggle.Enabled = false;
        }
        Tick();
    }

    private void OpenFiles()
    {
        if (runDir == null) return;
        using (var form = new FilesForm(runDir, state != State.Stopped))
        {
            form.ShowDialog(this);
        }
        Tick();
    }

    private void Restart()
    {
        if (state == State.Stopped) { StartServer(); return; }
        if (state != State.Online) return;
        restartPending = true;
        Append("Reiniciando: o servidor será parado e ligado de novo (o jar mais novo de libs é sincronizado).");
        StopServer();
    }

    private void OpenSettings()
    {
        if (runDir == null) return;
        using (var form = new SettingsForm(runDir, state == State.Stopped))
        {
            form.ShowDialog(this);
        }
        Tick();
    }

    private static void Style(Button b, Color color)
    {
        b.FlatStyle = FlatStyle.Flat;
        b.FlatAppearance.BorderSize = 0;
        b.BackColor = color;
        b.ForeColor = Color.White;
        b.Cursor = Cursors.Hand;
    }

    // ── Pastas e propriedades ──────────────────────────────────

    private static string FindRunDir(string start)
    {
        DirectoryInfo dir = new DirectoryInfo(start);
        for (int i = 0; i < 5 && dir != null; i++, dir = dir.Parent)
        {
            string candidate = Path.Combine(dir.FullName, "run");
            if (File.Exists(Path.Combine(candidate, "paper.jar"))) return candidate;
        }
        return null;
    }

    private string PropsPath { get { return Path.Combine(runDir, "server.properties"); } }

    private Dictionary<string, string> Props()
    {
        var map = new Dictionary<string, string>();
        if (!File.Exists(PropsPath)) return map;
        foreach (string line in ReadLinesShared(PropsPath))
        {
            int eq = line.IndexOf('=');
            if (line.StartsWith("#") || eq < 0) continue;
            map[line.Substring(0, eq).Trim()] = line.Substring(eq + 1).Trim();
        }
        return map;
    }

    /// <summary>Le linhas mesmo se o servidor estiver gravando o arquivo no momento (tenta de novo e nunca lanca).</summary>
    internal static List<string> ReadLinesShared(string path)
    {
        for (int attempt = 0; attempt < 4; attempt++)
        {
            try
            {
                using (var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete))
                using (var reader = new StreamReader(stream, Encoding.UTF8))
                {
                    var lines = new List<string>();
                    string line;
                    while ((line = reader.ReadLine()) != null) lines.Add(line);
                    return lines;
                }
            }
            catch (IOException) { Thread.Sleep(40); }
            catch (UnauthorizedAccessException) { Thread.Sleep(40); }
        }
        return new List<string>();
    }

    private int ServerPort()
    {
        string value;
        int port;
        return Props().TryGetValue("server-port", out value) && int.TryParse(value, out port) ? port : 25565;
    }

    private string Address() { return "localhost:" + ServerPort(); }

    /// <summary>Liga o RCON (somente para este aplicativo) e gera uma senha aleatória, se faltar.</summary>
    private void EnsureRcon()
    {
        var props = Props();
        string enabled, password;
        props.TryGetValue("enable-rcon", out enabled);
        props.TryGetValue("rcon.password", out password);
        if (enabled == "true" && !string.IsNullOrEmpty(password)) return;
        var bytes = new byte[12];
        using (var rng = RandomNumberGenerator.Create()) rng.GetBytes(bytes);
        string generated = BitConverter.ToString(bytes).Replace("-", "").ToLowerInvariant();
        var lines = File.Exists(PropsPath) ? ReadLinesShared(PropsPath) : new List<string>();
        SetProp(lines, "enable-rcon", "true");
        SetProp(lines, "rcon.password", string.IsNullOrEmpty(password) ? generated : password);
        File.WriteAllLines(PropsPath, lines, new UTF8Encoding(false));
    }

    private static void SetProp(List<string> lines, string key, string value)
    {
        for (int i = 0; i < lines.Count; i++)
        {
            if (lines[i].StartsWith(key + "="))
            {
                lines[i] = key + "=" + value;
                return;
            }
        }
        lines.Add(key + "=" + value);
    }

    // ── Java e plugin ──────────────────────────────────────────

    private string FindJava()
    {
        string jdks = Path.Combine(root, ".gradle-home", "jdks");
        if (Directory.Exists(jdks))
        {
            foreach (string dir in Directory.GetDirectories(jdks).OrderByDescending(d => d))
            {
                string java = Path.Combine(dir, "bin", "java.exe");
                if (File.Exists(java) && dir.Contains("25")) return java;
            }
        }
        string home = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrEmpty(home) && File.Exists(Path.Combine(home, "bin", "java.exe")))
            return Path.Combine(home, "bin", "java.exe");
        return "java";
    }

    /// <summary>Copia o jar mais novo de libs\ para plugins\ (conveniência de desenvolvimento).</summary>
    private void SyncPlugin()
    {
        string libs = Path.Combine(root, "libs");
        if (!Directory.Exists(libs)) return;
        string newest = null;
        Version best = new Version(0, 0);
        foreach (string file in Directory.GetFiles(libs, "Vertexx-*.jar"))
        {
            string name = Path.GetFileNameWithoutExtension(file).Substring("Vertexx-".Length);
            Version v;
            if (Version.TryParse(name, out v) && v > best) { best = v; newest = file; }
        }
        if (newest == null) return;
        string target = Path.Combine(runDir, "plugins", "Vertexx.jar");
        Directory.CreateDirectory(Path.GetDirectoryName(target));
        if (!File.Exists(target) || Hash(target) != Hash(newest))
        {
            try
            {
                File.Copy(newest, target, true);
                Append("Plugin atualizado para " + Path.GetFileName(newest) + ".");
            }
            catch (IOException) { Append("Nao foi possivel atualizar o plugin agora (arquivo em uso)."); }
        }
    }

    private static string Hash(string file)
    {
        using (var sha = SHA256.Create())
        using (var stream = File.OpenRead(file))
            return BitConverter.ToString(sha.ComputeHash(stream));
    }

    // ── Ligar / desligar ───────────────────────────────────────

    private void Toggle()
    {
        if (state == State.Stopped) StartServer();
        else if (state == State.Online || state == State.Starting) StopServer();
    }

    private void StartServer()
    {
        try
        {
            EnsureRcon();
            ServerConfig.EnsureLogConfig(runDir);
            SyncPlugin();
            string java = FindJava();
            var info = new ProcessStartInfo("cmd.exe",
                "/c \"\"" + java + "\" -Xmx" + ServerConfig.MemoryMb(runDir) + "M -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8"
                + " -Dlog4j.configurationFile=log4j2.xml,vertexx-log4j2.xml -Dterminal.jline=false -Dterminal.ansi=false"
                + " -jar paper.jar --nogui > console.log 2> console.err\"")
            {
                WorkingDirectory = runDir,
                UseShellExecute = false,
                CreateNoWindow = true,
                WindowStyle = ProcessWindowStyle.Hidden
            };
            string tmp = Path.Combine(Environment.GetEnvironmentVariable("PUBLIC") ?? "C:\\Users\\Public", "vertexx-gradle-tmp");
            Directory.CreateDirectory(tmp);
            // contorna o bug do Java com nomes de usuário acentuados no Windows
            info.EnvironmentVariables["JAVA_TOOL_OPTIONS"] = "-Djdk.net.unixdomain.tmpdir=" + tmp.Replace('\\', '/');
            launched = Process.Start(info);
            state = State.Starting;
            startedAt = DateTime.Now;
            logPos = -1;
            errPos = -1;
            log.Clear();
            Append("Iniciando o servidor…");
        }
        catch (Exception ex)
        {
            Append("Falha ao iniciar: " + ex.Message);
        }
        Tick();
    }

    private void StopServer()
    {
        state = State.Stopping;
        stoppingAt = DateTime.Now;
        Append("Parando o servidor (salvando os dados)…");
        var props = Props();
        ThreadPool.QueueUserWorkItem(_ =>
        {
            try { Rcon("stop"); }
            catch (Exception) { /* o servidor fecha a conexão ao parar */ }
        });
        Tick();
    }

    private void KillServer()
    {
        foreach (int pid in ServerPids())
        {
            try { Process.GetProcessById(pid).Kill(); } catch { }
        }
        Append("Servidor encerrado à força.");
    }

    private void SendCommand()
    {
        string text = command.Text.Trim();
        if (text.Length == 0) return;
        if (state != State.Online)
        {
            Append("O servidor precisa estar online para receber comandos.");
            return;
        }
        command.Clear();
        Append("> " + text);
        ThreadPool.QueueUserWorkItem(_ =>
        {
            string reply;
            try { reply = Rcon(text); }
            catch (Exception ex) { reply = "Erro: " + ex.Message; }
            if (IsHandleCreated && !IsDisposed)
                BeginInvoke((Action)(() => Append(string.IsNullOrWhiteSpace(reply) ? "(sem resposta)" : reply.Trim())));
        });
    }

    // ── Estado ─────────────────────────────────────────────────

    private bool PortOpen()
    {
        try
        {
            using (var client = new TcpClient())
            {
                var result = client.BeginConnect("127.0.0.1", ServerPort(), null, null);
                bool ok = result.AsyncWaitHandle.WaitOne(250) && client.Connected;
                return ok;
            }
        }
        catch { return false; }
    }

    private List<int> ServerPids()
    {
        var pids = new List<int>();
        try
        {
            using (var searcher = new ManagementObjectSearcher(
                "SELECT ProcessId, CommandLine FROM Win32_Process WHERE Name = 'java.exe'"))
            {
                foreach (ManagementObject item in searcher.Get())
                {
                    string line = item["CommandLine"] as string;
                    if (line != null && line.Contains("paper.jar")) pids.Add(Convert.ToInt32(item["ProcessId"]));
                }
            }
        }
        catch { }
        return pids;
    }

    private bool Alive()
    {
        if (launched != null && !launched.HasExited) return true;
        if ((DateTime.Now - lastScan).TotalSeconds >= 3)
        {
            lastScan = DateTime.Now;
            externalAlive = ServerPids().Count > 0;
        }
        return externalAlive;
    }

    private void Tick()
    {
        if (runDir == null) { Render(State.Stopped); return; }
        bool online = PortOpen();
        bool alive = online || Alive();
        State next;
        if (state == State.Stopping)
        {
            next = alive ? State.Stopping : State.Stopped;
            if (!alive) { Append("Servidor parado."); externalAlive = false; }
        }
        else if (!alive) next = State.Stopped;
        else if (online) next = State.Online;
        else next = State.Starting;

        if (state != State.Online && next == State.Online) Append("Servidor online em " + Address() + ".");
        state = next;
        if (restartPending && state == State.Stopped)
        {
            restartPending = false;
            StartServer();
            return;
        }
        Render(state);
        TailLog();
    }

    private void Render(State s)
    {
        restart.Enabled = s == State.Online;
        files.Enabled = runDir != null;
        switch (s)
        {
            case State.Stopped:
                dot.ForeColor = Color.Gray; status.Text = "Servidor parado";
                toggle.Text = "Iniciar servidor"; toggle.BackColor = Accent; toggle.Enabled = runDir != null;
                break;
            case State.Starting:
                dot.ForeColor = Color.Orange; status.Text = "Iniciando…";
                toggle.Text = "Cancelar"; toggle.BackColor = Panel; toggle.Enabled = true;
                break;
            case State.Online:
                dot.ForeColor = Green; status.Text = "Servidor online";
                toggle.Text = "Parar servidor"; toggle.BackColor = Color.FromArgb(160, 50, 60); toggle.Enabled = true;
                break;
            default:
                dot.ForeColor = Color.Orange; status.Text = "Parando…";
                toggle.Text = "Parando…"; toggle.BackColor = Panel; toggle.Enabled = false;
                break;
        }
        if (!address.Text.StartsWith("Endereço copiado") || s == State.Stopped)
            address.Text = runDir == null ? "" : "Mundo: " + ServerConfig.WorldName(runDir) + "  •  " + Address() + "  (copiar)";
        force.Visible = s == State.Stopping && (DateTime.Now - stoppingAt).TotalSeconds > 45;
    }

    // ── Log ────────────────────────────────────────────────────

    private static readonly Color LogText = Color.FromArgb(200, 200, 215);
    private static readonly Color LogWarn = Color.FromArgb(240, 180, 70);
    private static readonly Color LogError = Color.FromArgb(240, 100, 110);
    private static readonly Color LogInfo = Color.FromArgb(150, 130, 255);

    private void Append(string line)
    {
        AppendColored(line, line.StartsWith("> ") ? LogInfo : LogText);
    }

    private void AppendColored(string line, Color color)
    {
        log.SelectionStart = log.TextLength;
        log.SelectionLength = 0;
        log.SelectionColor = color;
        log.AppendText(line + "\n");
        log.SelectionColor = LogText;
    }

    /// <summary>Ruído inofensivo que o Java/Windows escreve e que não deve aparecer como erro.</summary>
    private static bool IsNoise(string line)
    {
        return line.Contains("Picked up JAVA_TOOL_OPTIONS") || line.Contains("oshi.") || line.Contains("Perflib")
            || line.Contains("PerfCounter") || line.Contains("Pdh call failed") || line.Contains("com.sun.jna");
    }

    private static Color LineColor(string line, bool fromErr)
    {
        if (line.Contains(" ERROR]") || line.Contains("/ERROR]") || line.StartsWith("Exception") || line.StartsWith("Caused by")
            || line.StartsWith("\tat ") || line.Contains("Exception:")) return LogError;
        if (line.Contains(" WARN]") || line.Contains("/WARN]")) return LogWarn;
        return fromErr ? LogError : LogText;
    }

    private void TailLog()
    {
        string[] fresh1 = ReadNew("console.log", ref logPos);
        string[] fresh2 = ReadNew("console.err", ref errPos);
        if (fresh1.Length == 0 && fresh2.Length == 0) return;
        // quem está lendo o histórico (com texto selecionado) não é puxado para o fim
        bool follow = log.SelectionLength == 0;
        log.SuspendLayout();
        foreach (string line in fresh1) if (!IsNoise(line)) AppendColored(line, LineColor(line, false));
        foreach (string line in fresh2) if (!IsNoise(line)) AppendColored(line, LineColor(line, true));
        if (log.Lines.Length > 3000)
        {
            log.Select(0, log.GetFirstCharIndexFromLine(1000));
            log.SelectedText = "";
        }
        log.ResumeLayout();
        if (follow)
        {
            log.SelectionStart = log.TextLength;
            log.ScrollToCaret();
        }
    }

    /// <summary>Lê só o que foi escrito desde a última vez (linhas completas); recomeça se o arquivo foi recriado.</summary>
    private string[] ReadNew(string name, ref long pos)
    {
        string path = Path.Combine(runDir, name);
        if (!File.Exists(path)) return new string[0];
        try
        {
            using (var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete))
            {
                long length = stream.Length;
                bool first = pos < 0;
                if (length < pos) { pos = 0; if (name == "console.log") log.Clear(); }
                if (first) pos = Math.Max(0, length - 24000);
                if (length == pos) return new string[0];
                stream.Seek(pos, SeekOrigin.Begin);
                var buffer = new byte[length - pos];
                int read = 0;
                while (read < buffer.Length)
                {
                    int n = stream.Read(buffer, read, buffer.Length - read);
                    if (n <= 0) break;
                    read += n;
                }
                int last = Array.LastIndexOf(buffer, (byte)10, Math.Max(0, read - 1));
                if (last < 0) return new string[0];   // linha ainda incompleta
                long start = pos;
                pos += last + 1;
                string text = Encoding.UTF8.GetString(buffer, 0, last + 1);
                var lines = text.Replace("\r", "").Split('\n').ToList();
                if (lines.Count > 0 && lines[lines.Count - 1].Length == 0) lines.RemoveAt(lines.Count - 1);
                if (first && start > 0 && lines.Count > 0) lines.RemoveAt(0);   // começou no meio de uma linha
                return lines.ToArray();
            }
        }
        catch (IOException) { return new string[0]; /* arquivo em uso: tenta no próximo ciclo */ }
    }

    // ── RCON ───────────────────────────────────────────────────

    private string Rcon(string text)
    {
        var props = Props();
        string password, portText;
        props.TryGetValue("rcon.password", out password);
        props.TryGetValue("rcon.port", out portText);
        int port;
        if (!int.TryParse(portText, out port)) port = 25575;
        using (var client = new TcpClient())
        {
            client.ReceiveTimeout = 8000;
            client.SendTimeout = 8000;
            client.Connect("127.0.0.1", port);
            var stream = client.GetStream();
            RconSend(stream, 1, 3, password ?? "");
            if (RconRead(stream).Key == -1) throw new Exception("senha do RCON recusada (reinicie o servidor pelo aplicativo)");
            RconSend(stream, 2, 2, text);
            return RconRead(stream).Value;
        }
    }

    private static void RconSend(Stream stream, int id, int type, string body)
    {
        byte[] data = Encoding.UTF8.GetBytes(body);
        using (var ms = new MemoryStream())
        using (var w = new BinaryWriter(ms))
        {
            w.Write(10 + data.Length);
            w.Write(id);
            w.Write(type);
            w.Write(data);
            w.Write((byte)0);
            w.Write((byte)0);
            w.Flush();
            byte[] packet = ms.ToArray();
            stream.Write(packet, 0, packet.Length);
        }
    }

    private static KeyValuePair<int, string> RconRead(Stream stream)
    {
        int length = BitConverter.ToInt32(ReadExactly(stream, 4), 0);
        byte[] payload = ReadExactly(stream, length);
        int id = BitConverter.ToInt32(payload, 0);
        string body = Encoding.UTF8.GetString(payload, 8, Math.Max(0, length - 10));
        return new KeyValuePair<int, string>(id, body);
    }

    private static byte[] ReadExactly(Stream stream, int count)
    {
        byte[] buffer = new byte[count];
        int read = 0;
        while (read < count)
        {
            int n = stream.Read(buffer, read, count - read);
            if (n <= 0) throw new IOException("conexão encerrada");
            read += n;
        }
        return buffer;
    }

    // ── Fechar ─────────────────────────────────────────────────

    private void OnClosing(object sender, FormClosingEventArgs e)
    {
        if (state != State.Online && state != State.Starting) return;
        DialogResult answer = MessageBox.Show(
            "O servidor está ligado.\n\nSim = parar o servidor e sair\nNão = sair e deixar o servidor ligado",
            "Vertexx Server", MessageBoxButtons.YesNoCancel, MessageBoxIcon.Question);
        if (answer == DialogResult.Cancel) { e.Cancel = true; return; }
        if (answer == DialogResult.Yes)
        {
            try { Rcon("stop"); } catch (Exception) { }
        }
    }
}
