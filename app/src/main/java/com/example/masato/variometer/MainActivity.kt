package com.example.masato.variometer

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.*
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.TextView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.github.mikephil.charting.formatter.IValueFormatter
import com.github.mikephil.charting.utils.ViewPortHandler
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    val data= DataUtil.getEmptyDataInstance()
    val sensorEventListener=ImplSensorEventListener(data)

    private fun getStackTraceString(e:Throwable):String{
        // Pipe ストリームで出力ストリームと入力ストリームを接続する。
        val pipeOut = PipedWriter();
        val pipeIn = PipedReader(pipeOut);
        val out = PrintWriter(pipeOut);

        // Throwable の printStackTrace メソッドで出力ストリームに書き出す。
        e.printStackTrace(out);
        out.flush();
        out.close();

        // 入力ストリームから結果を取得する。
        val br = BufferedReader(pipeIn);
        val buf = StringBuffer();
        var line=""
        while (line!=null) {
            line=br.readLine() ?: break
            buf.append(line);
        }

        return buf.toString();
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("variometer","OnCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Initialize Sensor
        val sensorManager=getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val pressureSensor=sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        sensorManager.registerListener(sensorEventListener,pressureSensor,SensorManager.SENSOR_DELAY_NORMAL)

        //Get UI
        val valueTextView=findViewById<TextView>(R.id.valueTextView)
        val lineChart=findViewById<LineChart>(R.id.chart)
        val textView=findViewById<TextView>(R.id.textView)

        //Initialize Chart
        initChart()

        var tone:Tone?
        //try{
            tone=Tone()
            //val e=java.lang.Exception("testtesttest")
            //throw e

        val handler=Handler()
        val runnable= object:Runnable {
            override fun run() {
                //update text
                valueTextView.text="%1$.2f".format(data.getValue().last())


                //update chart
                lineChart.lineData.dataSets[0].addEntry(Entry(data.index.last().toFloat(),data.getValue().last()))
                lineChart.lineData.dataSets[1].addEntry(Entry(data.index.last().toFloat(),data.fileterdValue.last()))

                lineChart.lineData.notifyDataChanged()
                lineChart.notifyDataSetChanged()

                lineChart.invalidate()


                //calc variation[Pa/s]
                val variation=(data.fileterdValue.last()-data.fileterdValue[data.fileterdValue.lastIndex-1])/(data.time.last().timeInMillis-data.time[data.time.lastIndex-1].timeInMillis)*1000*100

                //play tone
                tone?.play(440+variation.toInt())

                handler.postDelayed(this,1000)
            }
        }
        handler.post(runnable)
            /*
        }catch(e:Exception){
            e.printStackTrace()
            textView.text=getStackTraceString(e)
            tone=null
        }*/

    }

    fun initChart(){
        val lineChart=findViewById<LineChart>(R.id.chart)

        //raw data
        val rawDataList= mutableListOf<Entry>(Entry(0f,0f))
        val rawLineDataSet= LineDataSet(rawDataList,"Raw")

        //filtered data
        val filteredDataList= mutableListOf<Entry>(Entry(0f,0f))
        val filteredLineDataSet= LineDataSet(filteredDataList,"Filtered")

        val lineData= LineData(rawLineDataSet,filteredLineDataSet)
        lineChart.data=lineData

        //appearance and interactive
        rawLineDataSet.setDrawCircles(false)

        filteredLineDataSet.setDrawCircles(false)
        filteredLineDataSet.lineWidth=2f
        filteredLineDataSet.color=Color.BLACK

        lineChart.legend.isEnabled=true
        lineChart.description.isEnabled=false

        lineChart.isDragEnabled=true
        lineChart.setTouchEnabled(true)

        lineChart.invalidate()
    }
}

class ImplSensorEventListener(data:Data):SensorEventListener{
    private val data=data
    private var count=0

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    override fun onSensorChanged(event: SensorEvent?) {
        if(event?.sensor?.type==Sensor.TYPE_PRESSURE){
            data.index.add(count)
            data.time.add(Calendar.getInstance())
            data.addValue(event.values[0])
            count++
        }


    }

}

class Data(var index:ArrayList<Int>, var time:ArrayList<Calendar>, private var value:ArrayList<Float>){
    var fileterdValue= arrayListOf<Float>()

    fun addValue(value: Float){
        this.value.add(value)

        fileterdValue.add(this.value.takeLast(10).average().toFloat())
    }
    fun getValue():ArrayList<Float>{
        return this.value
    }
}

object DataUtil{
    fun getEmptyDataInstance():Data{
        val index= arrayListOf<Int>()
        val time= arrayListOf<Calendar>()
        val value= arrayListOf<Float>()
        return  Data(index, time, value)
    }
}

class Tone{
    val SAMPLE_RATE=44100
    val LENGTH=1

    val audioAttributes=AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    val audioFormat=AudioFormat.Builder()
        .setSampleRate(SAMPLE_RATE)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setChannelMask(AudioFormat.CHANNEL_OUT_DEFAULT)
        .build()
    val audioTrack= AudioTrack.Builder()
        .setAudioAttributes(audioAttributes)
        .setAudioFormat(audioFormat)
        .setBufferSizeInBytes(SAMPLE_RATE*LENGTH)
        .build()

    fun play(frequency:Int){
        val buff=generateSinWave(frequency, SAMPLE_RATE)

        audioTrack.play()
        audioTrack.write(buff,0,buff.size)

        /*
        val handler=Handler()
        val runnable=object :Runnable{
            override fun run() {
                audioTrack.write(buff,0,buff.size)
            }

        }
        handler.post(runnable)
        */

        audioTrack.stop()
        //audioTrack.flush()

    }

    // 1second
    private fun generateSinWave(frequency: Int, samplingRate:Int):ByteArray{
        var buff=ByteArray(samplingRate)
        for(i in 0 .. samplingRate-1){
            buff[i]=(100*Math.sin(2*Math.PI/samplingRate*i*frequency)).toByte()
            //Log.d("buff",buff[i].toString())
        }

        return buff
    }
}

