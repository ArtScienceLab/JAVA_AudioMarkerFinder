package be.ugent.audiomarkersync;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.GeneralizedGoertzel;
import be.tarsos.dsp.pitch.Goertzel.FrequenciesDetectedHandler;

public class AudioMarkerFinder {
	
	//90 % of the signal is expected to not contain any content on the specified frequency
	private final static double RELATIVE_THRESHOLD = 0.95;
	
	//200 bits in total 
	private final static double CARDINALITY_THRESHOLD_PERCENT = 70; //Number of bits that need to be equal out of a total of 200 in template
	
	private final static int SAMPLE_RATE = 32000; //HZ
	private final static int BLOCK_SIZE = 128; //audio samples
	private final static int OVERLAP = 0; //audio samples
	
	private final static double SIGNAL_FREQUENCY = 480.5;//The sync signal frequency
	
	private final BitSet template;
	
	//300ms between pulses (start to start)
	//200ms pulse + 100ms pauze
	//32000Hz / 128 => 1 audio block = 4 ms at 32000Hz pulese is 50 blocks, pauze 25 blocks:
	private final int templateLength = 50+25+50+25+50;
	
	private final String source;
	public AudioMarkerFinder(String source) {
		this.source = source;
		
		template = new BitSet(templateLength);
		for(int i = 0 ; i < 50  ; i ++) {
			template.set(i);
			template.set(i+50+25);
			template.set(i+50+25+50+25);
		}
	}
	
	/**
	 * This method finds a frequency of an expected duration in a file.
	 * 
	 * Using a generalized goertzel algorithm it detects the power of a presence of a specified frequency.
	 * If this is above a certain threshold it is 1 or below 0. This bit string is compared with a template.
	 * 
	 * If the cardinality of this comparison is above a certain number then it is accepted as a match. 
	 *  
	 * Template:
	 *             _____     _____     ______
	 *            |     |___|     |___|      |
	 * Signal:
	 *             .. ..     ... .     .....
	 *               .   ...       ...
	 * 
	 * Binary NOT XOR:
	 *            11011 111 11101 111  11111      
	 *           
	 * @return
	 */
	public List<MarkerEvent> findMarkers() {
		double[] frequencies = {SIGNAL_FREQUENCY};
		List<MarkerEvent> events = new ArrayList<MarkerEvent>();
		
		AudioDispatcher adp = AudioDispatcherFactory.fromPipe(source , SAMPLE_RATE,BLOCK_SIZE,OVERLAP);
		final ArrayList<Double> powersQueue = new ArrayList<>(); 
		final TreeMap<Double,Double> valuesMap = new TreeMap<>();
		
		//adp.addAudioProcessor(new StopAudioProcessor(AUDIO_DURATION));
		adp.addAudioProcessor(new GeneralizedGoertzel(SAMPLE_RATE, BLOCK_SIZE, frequencies, new FrequenciesDetectedHandler() {
			public void handleDetectedFrequencies(double timestamp, double[] frequencies, double[] powers,
					double[] allFrequencies, double[] allPowers) {
					powersQueue.add(powers[0]);
					valuesMap.put(timestamp,powers[0]);
			}
		}));
		
		adp.run();
		
		//if it is a file that does not contain audio
		//powersqueue will be of length zero
		if(powersQueue.size() == 0) {
			return events;
		}
		
		int index = (int) (powersQueue.size() * RELATIVE_THRESHOLD);
		Collections.sort(powersQueue);
		double relativeThreshold = powersQueue.get(index);
		//System.out.println(relativeThreshold);
		
		double[] timeStamps = new double[valuesMap.size()];
		
		BitSet signal = new BitSet(valuesMap.size());
		
		int signalIndex = 0;
		for(Entry<Double,Double> entry : valuesMap.entrySet()){
			if(entry.getValue() >= relativeThreshold) {
				signal.set(signalIndex);
			}else {
				signal.clear(signalIndex);
			}
			timeStamps[signalIndex] = entry.getKey();
			signalIndex++;
		}
		
		/*
		for(int i = 0 ; i < valuesMapCopy.size() ; i++) {
			System.out.print(signal.get(i) ? "-" : "_");
		}
		System.out.println();
		*/
		
		signalIndex = 0;
		
		for(signalIndex = 0 ; signalIndex < valuesMap.size() - templateLength && signalIndex >= 0 ; signalIndex++) {
			
			signalIndex = signal.nextSetBit(signalIndex);
			if(signalIndex < 0) {
				break;
			}
			BitSet matcher = signal.get(signalIndex, signalIndex + templateLength);
			
			matcher.xor(template);
			int cardinality = templateLength - matcher.cardinality(); 
			
			/*
			System.out.print(timeStamps[signalIndex]) ;
			System.out.print(" ") ;
			System.out.print(cardinality) ;
			System.out.print(" ") ;
			for(int i = 0 ; i < templateLength ; i++) {
				System.out.print(matcher.get(i) ? "-" : "_");
			}
			System.out.println();
			*/
			
			if(cardinality > CARDINALITY_THRESHOLD_PERCENT * 2) {
				MarkerEvent e = new MarkerEvent();
				e.score = cardinality/2.0;
				e.timestamp = timeStamps[signalIndex];
				events.add(e);
			}
		}
		
		//order by score
		Collections.sort(events, new Comparator<MarkerEvent>() {
			@Override
			public int compare(MarkerEvent e1, MarkerEvent e2) {
				return Double.valueOf(e2.score).compareTo(Double.valueOf(e1.score));
			}});
		
		//filter out detections that are too close to each other (1000ms)
		List<MarkerEvent> filteredEvents = new ArrayList<MarkerEvent>();
		
		for(MarkerEvent e : events) {
			boolean duplicate = false;
			for(MarkerEvent m:filteredEvents) {
				boolean tooClose = Math.abs(m.timestamp - e.timestamp) < 1;
				duplicate = duplicate || tooClose;
			}
			if(!duplicate)
				filteredEvents.add(e);
		}
		
		return filteredEvents;
	}
}
