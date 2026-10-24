# スミレの利用範囲と出典

- 上流：<https://github.com/KazumaProject/JapaneseKeyboard>
- 固定リビジョン：`04767ceb93afb22c4a39492e4295fdeca8c8c870`
- コードの著作権：Copyright (c) 2024 Kazuma Naka。MIT License。
- `app/src/main/java/thirdparty/sumire/`：LOUDS、LOUDSWithTermId、TokenArray、SuccinctBitVector、ビット操作、圧縮配列、接続行列、補助モデル・関数。
- 元のパッケージ名を保持。元パスは `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/` 以下の各相対パスに対応。
- 改変：`converter/Extenstions.kt` の AndroidX SparseArray 拡張2件を標準Android APIの `size()` に置換し、AndroidXへの依存を除去。その他の取り込みコードは元のまま。
- アプリ独自：`SumireConverter.kt` のビーム探索、IMEの入力UI、PositiveFingerの5タブ・ポイント・クラウド処理。スミレの完全移植や公式派生製品ではありません。

## データ

`app/src/main/assets/sumire/` に上流のシステム辞書3ファイル、接続行列、品詞表を同梱。
各ファイルを固定リビジョンの Git blob SHA-1 と照合して取得。出典とハッシュは同ディレクトリの `provenance.json` に保存。
辞書データは一律MITではなく、Mozc、NAIST/IPADIC等の各出典の条件を保持します。
上流の `LICENSE`、`THIRD-PARTY-NOTICES.md`、`licenses/` をそのまま保存し、設定画面でライセンス全文を表示します。
上流のライセンス目録には、本アプリで採用していない追加辞書等の説明も含まれます。追加カテゴリ辞書、コーパス由来のN-gram、英語読み辞書、AIモデル自体は同梱していません。

コード・辞書を再配布するときはこの出典、MIT全文、辞書別の著作権・ライセンス表記を保持してください。
