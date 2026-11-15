// Gerenciador de arquivos do Vertexx Server: navega pela pasta 'run' (sem sair dela), copia, renomeia, exclui
// e troca o jar do plugin.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Windows.Forms;

internal sealed class FilesForm : Form
{
    private static readonly Color Bg = Color.FromArgb(24, 22, 34);
    private static readonly Color Panel = Color.FromArgb(36, 33, 52);
    private static readonly Color Accent = Color.FromArgb(127, 90, 240);
    private static readonly Color Danger = Color.FromArgb(160, 50, 60);
    private static readonly string[] TextExt = { ".txt", ".yml", ".yaml", ".json", ".properties", ".log", ".xml", ".md", ".cfg", ".conf", ".toml", ".mcmeta", ".err" };

    private readonly string runDir;
    private readonly bool serverRunning;
    private string current;
    private readonly Label pathLabel = new Label();
    private readonly ListView list = new ListView();

    public FilesForm(string runDir, bool serverRunning)
    {
        this.runDir = Path.GetFullPath(runDir).TrimEnd('\\');
        this.serverRunning = serverRunning;
        current = this.runDir;

        Text = "Arquivos do servidor";
        ClientSize = new Size(760, 500);
        MinimumSize = new Size(640, 380);
        BackColor = Bg;
        ForeColor = Color.White;
        Font = new Font("Segoe UI", 9.5f);
        StartPosition = FormStartPosition.CenterParent;

        pathLabel.SetBounds(16, 12, 728, 22);
        pathLabel.Font = new Font("Consolas", 10f);
        pathLabel.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;

        Button up = Tool("↑ Subir", 16, 40, 80, () => Navigate(Path.GetDirectoryName(current)));
        Button refresh = Tool("Atualizar", 102, 40, 80, Reload);
        Button add = Tool("Adicionar arquivos…", 188, 40, 150, AddFiles);
        Button mkdir = Tool("Nova pasta", 344, 40, 96, NewFolder);
        Button rename = Tool("Renomear", 446, 40, 90, Rename);
        Button delete = Tool("Excluir", 542, 40, 80, Delete);
        delete.BackColor = Danger;
        Button explorer = Tool("Explorer", 628, 40, 116, () => Process.Start("explorer.exe", "\"" + current + "\""));

        list.SetBounds(16, 76, 728, 372);
        list.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        list.View = View.Details;
        list.FullRowSelect = true;
        list.MultiSelect = true;
        list.HideSelection = false;
        list.BorderStyle = BorderStyle.None;
        list.BackColor = Color.FromArgb(14, 13, 20);
        list.ForeColor = Color.FromArgb(220, 220, 235);
        list.Font = new Font("Segoe UI", 9.5f);
        list.Columns.Add("Nome", 360);
        list.Columns.Add("Tamanho", 90, HorizontalAlignment.Right);
        list.Columns.Add("Modificado", 150);
        list.AllowDrop = true;
        list.DoubleClick += (s, e) => OpenSelected();
        list.KeyDown += (s, e) =>
        {
            if (e.KeyCode == Keys.Enter) OpenSelected();
            else if (e.KeyCode == Keys.Delete) Delete();
            else if (e.KeyCode == Keys.F2) Rename();
            else if (e.KeyCode == Keys.Back) Navigate(Path.GetDirectoryName(current));
        };
        list.DragEnter += (s, e) =>
        {
            e.Effect = e.Data.GetDataPresent(DataFormats.FileDrop) ? DragDropEffects.Copy : DragDropEffects.None;
        };
        list.DragDrop += (s, e) =>
        {
            string[] dropped = e.Data.GetData(DataFormats.FileDrop) as string[];
            if (dropped != null) CopyIn(dropped);
        };

        Button swap = Tool("Trocar jar do plugin…", 16, 458, 180, SwapPluginJar);
        swap.BackColor = Accent;
        swap.Anchor = AnchorStyles.Bottom | AnchorStyles.Left;
        Label hint = new Label();
        hint.Text = "Duplo clique abre • Delete exclui • F2 renomeia • arraste arquivos para copiar";
        hint.ForeColor = Color.Silver;
        hint.SetBounds(206, 463, 538, 20);
        hint.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;

        Controls.AddRange(new Control[] { pathLabel, up, refresh, add, mkdir, rename, delete, explorer, list, swap, hint });
        Reload();
    }

    private Button Tool(string text, int x, int y, int w, Action action)
    {
        var b = new Button();
        b.Text = text;
        b.FlatStyle = FlatStyle.Flat;
        b.FlatAppearance.BorderSize = 0;
        b.BackColor = Panel;
        b.ForeColor = Color.White;
        b.Cursor = Cursors.Hand;
        b.SetBounds(x, y, w, 28);
        b.Click += (s, e) => action();
        return b;
    }

    // ── Navegação ──────────────────────────────────────────────

    private bool Inside(string path)
    {
        string full = Path.GetFullPath(path).TrimEnd('\\');
        return full.Equals(runDir, StringComparison.OrdinalIgnoreCase)
            || full.StartsWith(runDir + "\\", StringComparison.OrdinalIgnoreCase);
    }

    private void Navigate(string path)
    {
        if (path == null || !Inside(path) || !Directory.Exists(path)) return;
        current = Path.GetFullPath(path).TrimEnd('\\');
        Reload();
    }

    private void Reload()
    {
        list.BeginUpdate();
        list.Items.Clear();
        try
        {
            foreach (string dir in Directory.GetDirectories(current).OrderBy(d => d, StringComparer.OrdinalIgnoreCase))
            {
                var info = new DirectoryInfo(dir);
                var item = new ListViewItem("📁 " + info.Name) { Tag = dir };
                item.SubItems.Add("");
                item.SubItems.Add(info.LastWriteTime.ToString("dd/MM/yyyy HH:mm"));
                item.ForeColor = Color.FromArgb(160, 140, 255);
                list.Items.Add(item);
            }
            foreach (string file in Directory.GetFiles(current).OrderBy(f => f, StringComparer.OrdinalIgnoreCase))
            {
                var info = new FileInfo(file);
                var item = new ListViewItem(info.Name) { Tag = file };
                item.SubItems.Add(FormatSize(info.Length));
                item.SubItems.Add(info.LastWriteTime.ToString("dd/MM/yyyy HH:mm"));
                list.Items.Add(item);
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, "Não consegui listar a pasta:\n" + ex.Message, Text);
        }
        list.EndUpdate();
        string relative = current.Length > runDir.Length ? current.Substring(runDir.Length) : "\\";
        pathLabel.Text = "run" + (relative == "\\" ? "" : relative);
    }

    private static string FormatSize(long bytes)
    {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024.0).ToString("0.#") + " KB";
        if (bytes < 1024L * 1024 * 1024) return (bytes / 1048576.0).ToString("0.#") + " MB";
        return (bytes / 1073741824.0).ToString("0.##") + " GB";
    }

    private List<string> Selected()
    {
        return list.SelectedItems.Cast<ListViewItem>().Select(i => (string)i.Tag).ToList();
    }

    private void OpenSelected()
    {
        List<string> sel = Selected();
        if (sel.Count != 1) return;
        string path = sel[0];
        if (Directory.Exists(path)) { Navigate(path); return; }
        try
        {
            string ext = Path.GetExtension(path).ToLowerInvariant();
            if (TextExt.Contains(ext)) Process.Start("notepad.exe", "\"" + path + "\"");
            else Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, "Não consegui abrir:\n" + ex.Message, Text);
        }
    }

    // ── Ações ──────────────────────────────────────────────────

    private void AddFiles()
    {
        using (var dialog = new OpenFileDialog())
        {
            dialog.Multiselect = true;
            dialog.Title = "Copiar para " + pathLabel.Text;
            if (dialog.ShowDialog(this) == DialogResult.OK) CopyIn(dialog.FileNames);
        }
    }

    private void CopyIn(IEnumerable<string> sources)
    {
        int copied = 0;
        foreach (string source in sources)
        {
            try
            {
                string target = Path.Combine(current, Path.GetFileName(source.TrimEnd('\\')));
                if (Directory.Exists(source)) { CopyDirectory(source, target); copied++; continue; }
                if (File.Exists(target) && MessageBox.Show(this, "'" + Path.GetFileName(target) + "' já existe. Substituir?",
                        Text, MessageBoxButtons.YesNo, MessageBoxIcon.Question) != DialogResult.Yes) continue;
                File.Copy(source, target, true);
                copied++;
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, "Falha ao copiar '" + Path.GetFileName(source) + "':\n" + ex.Message, Text);
            }
        }
        Reload();
    }

    private static void CopyDirectory(string source, string target)
    {
        Directory.CreateDirectory(target);
        foreach (string file in Directory.GetFiles(source)) File.Copy(file, Path.Combine(target, Path.GetFileName(file)), true);
        foreach (string dir in Directory.GetDirectories(source)) CopyDirectory(dir, Path.Combine(target, Path.GetFileName(dir)));
    }

    private void NewFolder()
    {
        string name = Ask("Nome da nova pasta:", "");
        if (string.IsNullOrWhiteSpace(name)) return;
        try
        {
            string path = Path.Combine(current, name.Trim());
            if (!Inside(path)) throw new InvalidOperationException("caminho fora da pasta do servidor");
            Directory.CreateDirectory(path);
        }
        catch (Exception ex) { MessageBox.Show(this, ex.Message, Text); }
        Reload();
    }

    private void Rename()
    {
        List<string> sel = Selected();
        if (sel.Count != 1) return;
        string old = sel[0];
        string name = Ask("Novo nome:", Path.GetFileName(old));
        if (string.IsNullOrWhiteSpace(name) || name == Path.GetFileName(old)) return;
        try
        {
            string target = Path.Combine(current, name.Trim());
            if (!Inside(target)) throw new InvalidOperationException("caminho fora da pasta do servidor");
            if (Directory.Exists(old)) Directory.Move(old, target); else File.Move(old, target);
        }
        catch (Exception ex) { MessageBox.Show(this, "Não consegui renomear:\n" + ex.Message, Text); }
        Reload();
    }

    private void Delete()
    {
        List<string> sel = Selected();
        if (sel.Count == 0) return;
        if (serverRunning && sel.Any(p => Path.GetFileName(p).Equals("paper.jar", StringComparison.OrdinalIgnoreCase)))
        {
            MessageBox.Show(this, "Não dá para excluir o paper.jar com o servidor ligado.", Text);
            return;
        }
        string what = sel.Count == 1 ? "'" + Path.GetFileName(sel[0]) + "'" : sel.Count + " itens";
        if (MessageBox.Show(this, "Excluir " + what + "?\nIsso não pode ser desfeito.", Text,
                MessageBoxButtons.YesNo, MessageBoxIcon.Warning, MessageBoxDefaultButton.Button2) != DialogResult.Yes) return;
        foreach (string path in sel)
        {
            try
            {
                if (!Inside(path) || path.Equals(runDir, StringComparison.OrdinalIgnoreCase)) continue;
                if (Directory.Exists(path)) Directory.Delete(path, true); else File.Delete(path);
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, "Não consegui excluir '" + Path.GetFileName(path) + "':\n" + ex.Message, Text);
            }
        }
        Reload();
    }

    /// <summary>Troca plugins\Vertexx.jar por outro jar (o servidor precisa estar parado: o Windows trava o arquivo).</summary>
    private void SwapPluginJar()
    {
        if (serverRunning)
        {
            MessageBox.Show(this, "Pare o servidor antes de trocar o jar (ou use Reiniciar depois de trocar na pasta libs).", Text);
            return;
        }
        using (var dialog = new OpenFileDialog())
        {
            dialog.Title = "Escolha o jar do Vertexx";
            dialog.Filter = "Plugin (*.jar)|*.jar";
            string libs = Path.Combine(Path.GetDirectoryName(runDir), "libs");
            if (Directory.Exists(libs)) dialog.InitialDirectory = libs;
            if (dialog.ShowDialog(this) != DialogResult.OK) return;
            try
            {
                string plugins = Path.Combine(runDir, "plugins");
                Directory.CreateDirectory(plugins);
                File.Copy(dialog.FileName, Path.Combine(plugins, "Vertexx.jar"), true);
                MessageBox.Show(this, "plugins\\Vertexx.jar agora é " + Path.GetFileName(dialog.FileName) + ".", Text);
            }
            catch (Exception ex) { MessageBox.Show(this, "Não consegui trocar o jar:\n" + ex.Message, Text); }
        }
        Reload();
    }

    private string Ask(string prompt, string initial)
    {
        using (var form = new Form())
        {
            form.Text = Text;
            form.ClientSize = new Size(380, 110);
            form.FormBorderStyle = FormBorderStyle.FixedDialog;
            form.StartPosition = FormStartPosition.CenterParent;
            form.MaximizeBox = false;
            form.MinimizeBox = false;
            form.BackColor = Bg;
            form.ForeColor = Color.White;
            var label = new Label { Text = prompt, Left = 14, Top = 12, Width = 350 };
            var box = new TextBox { Left = 14, Top = 36, Width = 352, Text = initial, BackColor = Panel, ForeColor = Color.White };
            var ok = new Button { Text = "OK", Left = 206, Top = 70, Width = 76, DialogResult = DialogResult.OK, FlatStyle = FlatStyle.Flat, BackColor = Accent, ForeColor = Color.White };
            var cancel = new Button { Text = "Cancelar", Left = 290, Top = 70, Width = 76, DialogResult = DialogResult.Cancel, FlatStyle = FlatStyle.Flat, BackColor = Panel, ForeColor = Color.White };
            form.Controls.AddRange(new Control[] { label, box, ok, cancel });
            form.AcceptButton = ok;
            form.CancelButton = cancel;
            box.SelectAll();
            return form.ShowDialog(this) == DialogResult.OK ? box.Text : null;
        }
    }
}
