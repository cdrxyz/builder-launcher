package xyz.cdr.builderlauncher.read

object ArticleMedia {
    var player: ArticlePlayer? = null
    var title: String = ""

    fun bind(player: ArticlePlayer) {
        this.player = player
    }

    fun note(title: String) {
        this.title = title
    }
}
