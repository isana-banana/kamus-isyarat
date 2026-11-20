package com.kamusisyarat.speechpractice
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity:ComponentActivity(){
 private var recognizer:SpeechRecognizer?=null
 private var state by mutableStateOf(UiState())
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){if(it)listen()}
 override fun onCreate(b:Bundle?){super.onCreate(b);setContent{MaterialTheme{Screen(state,{state=state.copy(mode=it)},{state=state.copy(target=it)},::requestAudio)}}}
 private fun requestAudio(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)listen()else permission.launch(Manifest.permission.RECORD_AUDIO)}
 private fun listen(){
  recognizer?.destroy()
  recognizer=SpeechRecognizer.createSpeechRecognizer(this).also{sr->sr.setRecognitionListener(object:RecognitionListener{
   override fun onReadyForSpeech(p:Bundle?){state=state.copy(status="Mendengarkan…")}
   override fun onBeginningOfSpeech(){state=state.copy(status="Mendeteksi suara…")}
   override fun onEndOfSpeech(){state=state.copy(status="Memproses…")}
   override fun onError(e:Int){state=state.copy(status=err(e),listening=false)}
   override fun onResults(b:Bundle?){val t=b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty();state=state.copy(result=t,score=if(state.mode==Mode.PRACTICE)Similarity.score(state.target,t)else null,status=if(t.isBlank())"Suara tidak membentuk kata" else "Selesai",listening=false)}
   override fun onPartialResults(b:Bundle?){val t=b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty();if(t.isNotBlank())state=state.copy(result=t)}
   override fun onRmsChanged(v:Float){};override fun onBufferReceived(b:ByteArray?){};override fun onEvent(t:Int,b:Bundle?){}
  })}
  val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"id-ID");putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3)}
  state=state.copy(listening=true,result="",score=null,status="Memulai…");recognizer?.startListening(i)
 }
 private fun err(e:Int)=when(e){SpeechRecognizer.ERROR_NO_MATCH->"Suara tidak membentuk kata";SpeechRecognizer.ERROR_AUDIO->"Kesalahan audio";SpeechRecognizer.ERROR_NETWORK->"Jaringan bermasalah";SpeechRecognizer.ERROR_SPEECH_TIMEOUT->"Tidak ada ucapan";SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS->"Izin mikrofon diperlukan";else->"Pengenalan suara gagal"}
 override fun onDestroy(){recognizer?.destroy();super.onDestroy()}
}
private enum class Mode{PRACTICE,FREE}
private data class UiState(val mode:Mode=Mode.PRACTICE,val target:String="MA",val result:String="",val score:Int?=null,val status:String="Siap",val listening:Boolean=false)
@Composable fun Screen(s:UiState,onMode:(Mode)->Unit,onTarget:(String)->Unit,onListen:()->Unit){
 val c=when{ s.score==null->MaterialTheme.colorScheme.primary;s.score>=85->Color(0xFF2E7D32);s.score>=60->Color(0xFFF9A825);else->Color(0xFFC62828)}
 Column(Modifier.fillMaxSize().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
  Text("Latihan Bicara",style=MaterialTheme.typography.headlineMedium);Spacer(Modifier.height(12.dp))
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(s.mode==Mode.PRACTICE,{onMode(Mode.PRACTICE)},{Text("Latihan")});FilterChip(s.mode==Mode.FREE,{onMode(Mode.FREE)},{Text("Bebas")})}
  Spacer(Modifier.height(18.dp))
  if(s.mode==Mode.PRACTICE){OutlinedTextField(s.target,onTarget,label={Text("Target")},singleLine=true);Spacer(Modifier.height(16.dp))}
  Card(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
   Text(if(s.mode==Mode.PRACTICE)"Ucapkan:" else "Ucapkan apa saja")
   if(s.mode==Mode.PRACTICE)Text(s.target,style=MaterialTheme.typography.displaySmall)
   Spacer(Modifier.height(12.dp));Text(if(s.result.isBlank())"—" else s.result,style=MaterialTheme.typography.headlineSmall)
   s.score?.let{Text("$it%",style=MaterialTheme.typography.displaySmall,color=c);Text(if(it>=85)"Hampir sesuai" else if(it>=60)"Agak mirip" else "Tidak cocok",color=c)}
  }}
  Spacer(Modifier.height(20.dp));Button(onClick=onListen,enabled=!s.listening){Text(if(s.listening)"Mendengarkan…" else "🎤 Mulai")}
  Spacer(Modifier.height(10.dp));Text(s.status)
 }
}
