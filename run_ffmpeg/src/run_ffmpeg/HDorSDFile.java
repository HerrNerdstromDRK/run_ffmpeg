package run_ffmpeg;

public class HDorSDFile implements Comparable< HDorSDFile >
{
	public String name = null ;
	
	public HDorSDFile( String name )
	{
		this.name = name ;
	}
	
	@Override
	public int compareTo( HDorSDFile rhs )
	{
		return name.compareTo( rhs.name ) ;
	}
}
