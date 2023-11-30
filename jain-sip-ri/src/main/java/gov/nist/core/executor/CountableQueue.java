package gov.nist.core.executor;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class CountableQueue<T extends Task>
{
	private ConcurrentLinkedDeque<T> queue = new ConcurrentLinkedDeque<T>();
	private AtomicInteger counter = new AtomicInteger(0);

	public void offerLast(T element)
	{
		counter.incrementAndGet();
		queue.offerLast(element);
	}

	public void offerFirst(T element)
	{
		counter.incrementAndGet();
		queue.offerFirst(element);
	}

	public T take() throws InterruptedException
	{
		T element = queue.pollFirst();
		if (element != null)
			counter.decrementAndGet();
		return element;
	}

	// public T poll(long timeout, TimeUnit unit) throws InterruptedException
	// {
	// 	T element = queue.poll(timeout, unit);
	// 	if (element != null)
	// 		counter.decrementAndGet();
	// 	return element;
	// }

	public boolean thresholdReached(int threshold,int timeThreshold)
	{
		T task = queue.peek();
		if (task != null && task.getStartTime()<System.currentTimeMillis()-timeThreshold)
			return counter.get() >= threshold;
			
		return false;		
	}

	public int getCounter()
	{
		return counter.get();
	}

	public int size()
	{
		return queue.size();
	}

	public void clear()
	{
		queue.clear();
		counter.set(0);
	}
}

