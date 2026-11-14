# Resource pack (opcional)

Esta pasta é o lugar dos arquivos-fonte do pacote visual do servidor (ícones de UI, texturas
de Waystones e itens). **Ainda não há um pacote pronto**: o plugin já tem toda a integração,
mas ela fica inativa enquanto `resource-pack.url` estiver vazio no `config.yml`.

## Como ativar

1. Monte o pacote aqui (`pack.mcmeta`, `assets/...`) e compacte em um `.zip`.
2. Hospede o `.zip` em um link direto (por exemplo, como arquivo de uma release deste repositório).
3. Calcule o SHA-1 do arquivo:
   ```powershell
   (Get-FileHash pacote.zip -Algorithm SHA1).Hash.ToLower()
   ```
4. Preencha no `plugins/Vertexx/config.yml` do servidor:
   ```yaml
   resource-pack:
     url: "https://.../pacote.zip"
     sha1: "<hash de 40 caracteres>"
   ```

## Como o jogador vê

Depois de escolher o reino (ou ao entrar, se já tiver um), o jogador recebe um convite no chat com
os botões **[ ✔ Carregar ]** e **[ ✖ Agora não ]**. A escolha fica salva e pode ser trocada com
`/pacote`. Nada é forçado: quem recusa joga com o visual vanilla.
