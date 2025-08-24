package run_ffmpeg;

import com.google.gson.Gson;

public class Ollama_Test_ffprobeResult
{
	public class Ollama_Test_format
	{
		public String duration = null ;
	}
	
	public Ollama_Test_format format = null ;
	
//	public String duration = null ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
