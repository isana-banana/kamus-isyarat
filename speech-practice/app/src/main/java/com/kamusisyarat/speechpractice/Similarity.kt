package com.kamusisyarat.speechpractice
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
object Similarity {
    fun score(target:String,spoken:String):Int {
        val a=norm(target); val b=norm(spoken)
        if(a.isBlank()||b.isBlank()) return 0
        if(a==b)return 100
        val d=lev(a,b)
        return (100.0*(1.0-d.toDouble()/max(a.length,b.length))).toInt().coerceIn(0,100)
    }
    private fun norm(s:String)=Normalizer.normalize(s.lowercase(Locale.ROOT),Normalizer.Form.NFD)
        .replace("\p{M}+".toRegex(),"").replace("[^a-z0-9 ]".toRegex()," ")
        .replace("\s+".toRegex()," ").trim()
    private fun lev(a:String,b:String):Int {
        var p=IntArray(b.length+1){it}; var c=IntArray(b.length+1)
        for(i in 1..a.length){c[0]=i;for(j in 1..b.length)c[j]=minOf(c[j-1]+1,p[j]+1,p[j-1]+if(a[i-1]==b[j-1])0 else 1);val t=p;p=c;c=t}
        return p[b.length]
    }
}
