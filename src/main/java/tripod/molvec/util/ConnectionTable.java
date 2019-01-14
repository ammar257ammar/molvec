package tripod.molvec.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import gov.nih.ncats.chemkit.api.Atom;
import gov.nih.ncats.chemkit.api.Bond;
import gov.nih.ncats.chemkit.api.Chemical;
import gov.nih.ncats.chemkit.api.ChemicalBuilder;
import gov.nih.ncats.chemkit.api.Bond.BondType;
import gov.nih.ncats.chemkit.api.Bond.Stereo;
import tripod.molvec.Bitmap;
import tripod.molvec.CachedSupplier;
import tripod.molvec.algo.Tuple;
import tripod.molvec.util.ConnectionTable.Edge;

public class ConnectionTable{
	private List<Node> nodes = new ArrayList<Node>();
	private List<Edge> edges = new ArrayList<Edge>();

	private CachedSupplier<Map<Integer,List<Edge>>> _bondMap = CachedSupplier.of(()->_getEdgeMap());
	private CachedSupplier<Map<Node,Integer>> _nodeMap = CachedSupplier.of(()->_getNodeMap());
	
	
	public ConnectionTable addNode(Point2D p){
		nodes.add(new Node(p,"C"));
		resetCaches();
		return this;
	}
	
	public ConnectionTable setNodeToSymbol(Shape s, String sym){
		nodes.stream()
		.filter(n->s.contains(n.point.getX(),n.point.getY()))
		.forEach(n->{
			n.symbol=sym;
		});
		return this;
		
	}
	
	
	public Chemical toChemical(){
		ChemicalBuilder cb = new ChemicalBuilder();
		Atom[] atoms = new Atom[nodes.size()];
		
		for(int i=0;i<nodes.size();i++){
			Node n = nodes.get(i);
			atoms[i]=cb.addAtom(n.symbol,n.point.getX(),-n.point.getY());
		}
		for(Edge e : edges){
			if(e.getOrder()==1){
				Bond b=cb.addBond(atoms[e.n1],atoms[e.n2],BondType.SINGLE);
				if(e.getDashed()){
					b.setStereo(Stereo.DOWN);
				}
				if(e.getWedge()){
					b.setStereo(Stereo.UP);
				}
				
			}else if(e.getOrder()==2){
				cb.addBond(atoms[e.n1],atoms[e.n2],BondType.DOUBLE);
			}else if(e.getOrder()==3){
				cb.addBond(atoms[e.n1],atoms[e.n2],BondType.TRIPLE);
				
				
			}else{
				cb.addBond(atoms[e.n1],atoms[e.n2],BondType.SINGLE);
			}
		}
		return cb.build();
	}
	
	public ConnectionTable mergeNodesAverage(int n1, int n2){
		return mergeNodes(n1,n2,(node1,node2)->new Point2D.Double((node1.getX()+node2.getX())/2,(node1.getY()+node2.getY())/2));
	}
	
	public ConnectionTable mergeNodes(int n1, int n2, BinaryOperator<Point2D> op){
		mergeNodesGetTransform(n1,n2,op);
		return this;
	}
	
	public Map<Integer,Integer> mergeNodesGetTransform(int n1, int n2, BinaryOperator<Point2D> op){
		Point2D node1=nodes.get(n1).point;
		Point2D node2=nodes.get(n2).point;
		Point2D np = op.apply(node1, node2);
		int oldMax=nodes.size();
		
		int remNode=Math.max(n1, n2);
		int keepNode=Math.min(n1, n2);
		nodes.set(keepNode,new Node(np,nodes.get(n1).symbol));
		
		nodes.remove(remNode);
		for(Edge e: edges){
			if(e.n1==remNode){
				e.n1=keepNode;
			}
			if(e.n2==remNode){
				e.n2=keepNode;
			}
			if(e.n1>remNode){
				e.n1=e.n1-1;
			}
			if(e.n2>remNode){
				e.n2=e.n2-1;
			}
		}
		Map<Integer,Integer> oldToNew =new HashMap<>();
		for(int i=0;i<oldMax;i++){
			if(i<remNode){
				oldToNew.put(i, i);
			}
			if(i==remNode){
				oldToNew.put(i, keepNode);
			}
			if(i>remNode){
				oldToNew.put(i, i-1);
			}
		}
		resetCaches();
		return oldToNew;
	}
	
	public ConnectionTable mergeNodes(List<Integer> nlist, Function<List<Point2D>, Point2D> op){
		Point2D p = op.apply(nlist.stream().map(i->nodes.get(i).point).collect(Collectors.toList()));
		
		nlist=nlist.stream().sorted().collect(Collectors.toList());
		
		if(nlist.size()==1){
			int ni=nlist.get(0);
			nodes.get(ni).point=p;
		}else{
			for(int i=nlist.size()-1;i>=1;i--){
				int ni1=nlist.get(i);
				int ni2=nlist.get(i-1);
				mergeNodes(ni1,ni2,(p1,p2)->p);
			}
		}
		
		
		return this;
	}
	
	public ConnectionTable removeNode(int remNode){
		
		for(Edge e: edges){
			if(e.n1==remNode || e.n2==remNode){
				throw new IllegalStateException("Can't remove a node that is used in an edge");
			}
			if(e.n1>remNode){
				e.n1=e.n1-1;
			}
			if(e.n2>remNode){
				e.n2=e.n2-1;
			}
		}
		this.nodes.remove(remNode);
		resetCaches();
		return this;
	}
	
	public ConnectionTable removeEdge(Edge e) {
		this.edges.remove(e);
		resetCaches();
		return this;
	}
	
	public Map<Integer,Integer> mergeNodesGetTransform(List<Integer> nlist, Function<List<Point2D>, Point2D> op){
		Point2D p = op.apply(nlist.stream().map(i->nodes.get(i).point).collect(Collectors.toList()));
		
		nlist=nlist.stream().sorted().collect(Collectors.toList());
		
		Map<Integer,Integer> oldToNewMap=new HashMap<>();
		for(int i=0;i<this.nodes.size();i++){
			oldToNewMap.put(i, i);
		}
		
		
		if(nlist.size()==1){
			nodes.get(nlist.get(0)).point=p;
		}else{
			for(int i=nlist.size()-1;i>=1;i--){
				
				int ni1=nlist.get(i);
				int ni2=nlist.get(i-1);
				Map<Integer,Integer> oldNewNew=mergeNodesGetTransform(ni1,ni2,(p1,p2)->p);
				oldToNewMap=oldToNewMap.entrySet().stream()
				   .map(Tuple::of)
		//		   .peek(t->System.out.println("PFrom:"+t.k() + " to "+t.v()))
				   .map(Tuple.vmap(oi->oldNewNew.get(oi)))
		//		   .peek(t->System.out.println("From:"+t.k() + " to "+t.v()))
				   .collect(Tuple.toMap());
				
			}
		}
		return oldToNewMap;
	}
	
	public ConnectionTable cleanMeaninglessEdges(){
		this.edges=edges.stream()
				 		.filter(e->e.n1!=e.n2)
						.collect(Collectors.toList());

		resetCaches();
		
		return this;
	}
	
	public ConnectionTable cleanDuplicateEdges(BinaryOperator<Edge> combiner){
		edges=edges.stream().map(e->e.standardize())
		              .map(e->Tuple.of(e,e.n1+"_" + e.n2))
		              .collect(Collectors.toMap(t->t.v(),t-> t.k(), combiner))
		              .values()
		              .stream()
		              .collect(Collectors.toList());
		resetCaches();
		return this;
	}
	
	public ConnectionTable mergeNodesCloserThan(double maxDistance){
		boolean mergedOne = true;
		
		while(mergedOne){
			mergedOne=false;
			for(int i=0;i<nodes.size();i++){
				Point2D pnti=nodes.get(i).point;
				for(int j=i+1;j<nodes.size();j++){
					Point2D pntj = nodes.get(j).point;
					if(pnti.distance(pntj)<maxDistance){
						mergeNodesAverage(i,j);
						mergedOne=true;
						break;
					}
				}
				if(mergedOne)break;
			}
		}
		return this;
	}
	
	public ConnectionTable addEdge(int n1, int n2, int o){
		this.edges.add(new Edge(n1,n2,o));
		resetCaches();
		return this;
	}
	
	public List<Node> getNodesInsideShape(Shape s, double tol){
		List<Node> mnodes= new ArrayList<>();
		
		for(int i=nodes.size()-1;i>=0;i--){
			Point2D pn = nodes.get(i).point;
			if(GeomUtil.distanceTo(s,pn)<tol){
				mnodes.add(nodes.get(i));
			}
		}
		return mnodes;
	}
	
	public Tuple<Node,Double> getClosestNodeToShape(Shape s){
		return nodes.stream()
		     .map(n->Tuple.of(n,GeomUtil.distanceTo(s, n.point)).withVComparator())
		     .sorted()
		     .findFirst()
		     .orElse(null);
		
	}
	
	
	public ConnectionTable mergeAllNodesInside(Shape s, double tol,Predicate<Node> allow, Function<List<Point2D>, Point2D> merger){
		
		List<Integer> toMerge = new ArrayList<Integer>();
		for(int i=nodes.size()-1;i>=0;i--){
			Point2D pn = nodes.get(i).point;
			if(GeomUtil.distanceTo(s,pn)<tol){
				toMerge.add(i);
			}
		}
		toMerge=toMerge.stream()
		       .filter(i->allow.test(nodes.get(i)))
		        .collect(Collectors.toList());
			
		
		return this.mergeNodes(toMerge, merger);
	}
	
	public ConnectionTable mergeAllNodesInsideCenter(Shape s, double tol){
		Rectangle2D r=s.getBounds2D();
		Point2D p = new Point2D.Double(r.getCenterX(),r.getCenterY());
		return mergeAllNodesInside(s,tol,n->true,(l)->p);
	}
	
	public ConnectionTable mergeNodesExtendingTo(List<Shape> shapes){
		double avg = this.getAverageBondLength();
		edges.stream()
		     .filter(e->e.getBondDistance()<avg)
		     .forEach(e->{
		    	 Point2D p1=e.getPoint1();
		    	 Point2D p2=e.getPoint2();
		    	 double minDistp1=Double.MAX_VALUE;
		    	 double minDistp2=Double.MAX_VALUE;
		    	 Shape closest1=null;
		    	 Shape closest2=null;
		    	 
		    	 for(Shape s: shapes){
		    		 double d1=GeomUtil.distanceTo(s, p1);
		    		 double d2=GeomUtil.distanceTo(s, p2);
		    		 if(d1<minDistp1){
		    			 minDistp1=d1;
		    			 closest1=s;
		    		 }
		    		 if(d2<minDistp2){
		    			 minDistp2=d2;
		    			 closest2=s;
		    		 }
		    	 }
		    	 if(minDistp1>avg/2 && minDistp2>avg/2){
		    		 return;
		    	 }
		    	 Node closestNode = this.nodes.get(e.n1);
		    	 Point2D newPoint = null;
		    	 Line2D newLine = null;
		    	 if(minDistp1>minDistp2){
		    		 closestNode = this.nodes.get(e.n2);
		    		 newPoint=new Point2D.Double(closest2.getBounds2D().getCenterX(),closest2.getBounds2D().getCenterY());
		    		 newLine=new Line2D.Double(p1,newPoint);
		    	 }else{
		    		 newPoint=new Point2D.Double(closest1.getBounds2D().getCenterX(),closest1.getBounds2D().getCenterY());
		    		 newLine=new Line2D.Double(p2,newPoint);
		    	 }
		    	 if(GeomUtil.length(newLine)> 1.3*avg){
		    		 return;
		    	 }
		    	 double cosTheta = Math.abs(GeomUtil.cosTheta(newLine,e.getLine()));
		    	 if(cosTheta<Math.cos(20.0*Math.PI/180.0)){
		    		 return;
		    	 }
		    	 closestNode.point=newPoint;
		     });
		this.mergeNodesCloserThan(avg/20);
		
		return this;
	}
	
	public ConnectionTable mergeAllNodesOnParLines(){
		Map<Line2D,Edge> edgeMap = this.edges.stream().collect(Collectors.toMap(e->e.getLine(),e->e ));
		
		Map<Integer,Integer> oldToNewMap = IntStream.range(0,this.nodes.size())
		         .mapToObj(i->i)
		         .collect(Collectors.toMap(i->i, i->i));
		
		List<LinkedHashSet<Integer>> mergeNodes=GeomUtil.groupMultipleBonds(edgeMap.keySet().stream().collect(Collectors.toList()),5*Math.PI/180, 2, .8, 0)
		.stream()
		.filter(l->l.size()>1)
		.map(l->{
			Line2D keep=l.stream()
			 .map(l1->Tuple.of(-GeomUtil.length(l1),l1).withKComparator())
			 .sorted()
			 .findFirst()
			 .get()
			 .v();
			Edge keepEdge=edgeMap.get(keep);
			LinkedHashSet<Integer> mergeNode1 = new LinkedHashSet<Integer>();
			LinkedHashSet<Integer> mergeNode2 = new LinkedHashSet<Integer>();
			mergeNode1.add(keepEdge.n1);
			mergeNode2.add(keepEdge.n2);
			
			Point2D node1Point = keepEdge.getPoint1();
			Point2D node2Point = keepEdge.getPoint2();
			
			l.stream()
			 .map(l1->edgeMap.get(l1))
			 .filter(e->e!=keepEdge)
			 .forEach(me->{
				 double distance1to1=me.getPoint1().distance(node1Point);
				 double distance1to2=me.getPoint1().distance(node2Point);
				 double distance2to1=me.getPoint2().distance(node1Point);
				 double distance2to2=me.getPoint2().distance(node2Point);
				 if(distance1to1<distance1to2){
					 mergeNode1.add(me.n1);
				 }else{
					 mergeNode2.add(me.n1);
				 }
				 if(distance2to1<distance2to2){
					 mergeNode1.add(me.n2);
				 }else{
					 mergeNode2.add(me.n2);
				 }					 
			 });				
			List<LinkedHashSet<Integer>> toMerge=new ArrayList<LinkedHashSet<Integer>>();
			toMerge.add(mergeNode1);
			toMerge.add(mergeNode2);
			return toMerge;
		})
		.flatMap(l->l.stream())
		.filter(l->l.size()>1)
		.collect(Collectors.toList());
		
		mergeNodes.forEach(ls->{
			List<Integer> toMerge=ls.stream().map(i->oldToNewMap.get(i)).collect(Collectors.toList());
			
			Point2D keeper=this.nodes.get(toMerge.get(0)).point;
			toMerge=toMerge.stream().distinct().collect(Collectors.toList());
			if(toMerge.size()<=1)return;

			Map<Integer,Integer> newTrans=mergeNodesGetTransform(toMerge,(pts)->keeper);
			for(int i : oldToNewMap.keySet()){
				int oldMap=oldToNewMap.get(i);

				//System.out.println(newTrans.toString());
				int newMap=newTrans.get(oldMap);
				oldToNewMap.put(i, newMap);
			}
		});
		return this;
	}
	
	public ConnectionTable createNodesOnIntersectingLines(){
		
		boolean splitOne =true;
		while(splitOne){
			splitOne=false;
		
			for(int i=0;i<edges.size();i++){
				Edge e1=edges.get(i);
				Set<Integer> i1set=e1.getNodeSet();
				for(int j=0;j<edges.size();j++){
					Edge e2=edges.get(j);
					if(i1set.contains(e2.n1) || i1set.contains(e2.n2)){
						continue;
					}
					if(e1.getLine().intersectsLine(e2.getLine())){
						Point2D np=GeomUtil.intersection(e1.getLine(),e2.getLine());
						int nodeNew = this.nodes.size();
						this.addNode(np);
						Edge nedge1 = new Edge(e1.n1, nodeNew, e1.getOrder());
						Edge nedge2 = new Edge(e1.n2, nodeNew, e1.getOrder());
						Edge nedge3 = new Edge(e2.n1, nodeNew, e2.getOrder());
						Edge nedge4 = new Edge(e2.n2, nodeNew, e2.getOrder());
						edges.set(i, nedge1);
						edges.set(j, nedge2);
						edges.add(nedge3);
						edges.add(nedge4);
						splitOne=true;
						break;
					}
				}
				if(splitOne)break;
			}
		}
		resetCaches();
		return this;
	}
	
	private void resetCaches(){
		_bondMap.resetCache();
		_nodeMap.resetCache();
		
	}
	
	public double getAverageBondLength(){
		return edges.stream().mapToDouble(e->e.getBondDistance()).average().orElse(0);
	}
	public class Node{
		Point2D point;
		String symbol="C";
		
		public Node(Point2D p, String s){
			this.point=p;
			this.symbol=s;				
		}
		public double distanceTo(Node n2){
			return this.point.distance(n2.point);
		}
		
		public List<Edge> getEdges(){
			return getEdgeMap().getOrDefault(getIndex(), new ArrayList<>());
		}
		
		public int getIndex(){
			Integer ind=getNodeMap().get(this);
			if(ind==null)return -1;
			return ind;
		}
		
		public boolean equals(Object o){
			if(o==null)return false;
			if(!(o instanceof Node))return false;
			return o==this;
		}
		
		public int hashCode(){
			return point.hashCode() ^ symbol.hashCode();
		}
		public Point2D getPoint() {
			return this.point;
		}
		public String getSymbol() {
			return this.symbol;
		}
		
	}
	public class Edge{
		int n1;
		int n2;
		private int order;
		boolean isWedge=false;
		boolean isDash=false;
		public Edge(int n1, int n2, int o){
			this.n1=n1;
			this.n2=n2;
			this.setOrder(o);
		}
		
		public Edge setDashed(boolean d){
			this.isDash=d;
			return this;
		}
		public Edge setWedge(boolean d){
			this.isWedge=d;
			return this;
		}
		
		public boolean getDashed(){
			return this.isDash;
		}
		public boolean getWedge(){
			return this.isWedge;
		}
		
		
		
		public double getBondDistance(){
			return ConnectionTable.this.nodes.get(n1).point.distance(ConnectionTable.this.nodes.get(n2).point);
		}
		public Edge standardize(){
			if(n2<n1){
				int t=this.n1;
				this.n1=this.n2;
				this.n2=t;
			}
			return this;
		}
		public Set<Integer> getNodeSet(){
			Set<Integer> iset = new HashSet<Integer>();
			iset.add(n1);
			iset.add(n2);
			return iset;
			
		}
		public Line2D getLine(){
			Point2D p1 = ConnectionTable.this.nodes.get(n1).point;
			Point2D p2 = ConnectionTable.this.nodes.get(n2).point;
			return new Line2D.Double(p1, p2);
		}
		public Point2D getPoint1(){
			return ConnectionTable.this.nodes.get(n1).point;
		}
		public Point2D getPoint2(){
			return ConnectionTable.this.nodes.get(n2).point;
		}
		
		public Node getRealNode1(){
			return ConnectionTable.this.nodes.get(n1);
		}
		public Node getRealNode2(){
			return ConnectionTable.this.nodes.get(n2);
		}
		
		public int getOrder() {
			
			return this.order;
		}

		public Edge switchNodes() {
			int t=this.n1;
			this.n1=this.n2;
			this.n2=t;
			return this;
			
		}

		public void setOrder(int order) {
			this.order = order;
		}
		
	}
	public void draw(Graphics2D g2) {
		int sx=1;
		Stroke old = g2.getStroke();
		
		Stroke dashed = new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3}, 0);
		Stroke wedge = new BasicStroke(5f);
        
		
		nodes.stream().map(n->n.point)
		.forEach(p->{
			g2.draw(new Ellipse2D.Double((p.getX()-2f/sx), (p.getY()-2f/sx), 4f/sx, 4f/sx));
		});
		
		edges.forEach(l->{
			if(l.getOrder()==1){
				g2.setPaint(Color.BLACK);
				if(l.isDash){
					g2.setStroke(dashed);						
				}
				if(l.isWedge){
					g2.setStroke(wedge);
				}
				
			}else if(l.getOrder()==2){
				g2.setPaint(Color.RED);
			}else if(l.getOrder()==3){
				g2.setPaint(Color.GREEN);
			}
			
			g2.draw(l.getLine());
			g2.setStroke(old);
		});
		
	}

	public List<Edge> getEdges() {
		return this.edges;
	}
	
	public List<Tuple<Edge,Double>> getTolerancesForAllEdges(Bitmap bm){
		return this.edges.stream()
		          .map(e->Tuple.of(e,bm.getLineLikeScore(e.getLine())))
		          .collect(Collectors.toList());
	}
	
	public List<Tuple<Edge,Double>> getDashLikeScoreForAllEdges(Bitmap bm){
		return this.edges.stream()
		          .map(e->Tuple.of(e,bm.getDashLikeScore(e.getLine())))
		          .collect(Collectors.toList());
	}

	public ConnectionTable makeMissingBondsToNeighbors(Bitmap bm, double d, double tol, List<Shape> OCRSet, double ocrTol,Consumer<Tuple<Double,Edge>> econs) {
		double avg=this.getAverageBondLength();
		Map<Integer,Set<Integer>> nmap = new HashMap<>();
		
		Set<Integer> nullSet = new HashSet<Integer>();
		
		
		edges.forEach(e->{
			nmap.computeIfAbsent(e.n1,k->new HashSet<>()).add(e.n2);
			nmap.computeIfAbsent(e.n2,k->new HashSet<>()).add(e.n1);
		});
		for(int i =0 ;i<this.nodes.size();i++){
			Node n1=nodes.get(i);
			for(int j =i+1 ;j<this.nodes.size();j++){
				if(!nmap.getOrDefault(i,nullSet).contains(j)){
					Node n2=nodes.get(j);
					if(n1.distanceTo(n2)<avg*d){
						Tuple<Shape,Double> t1=GeomUtil.findClosestShapeTo(OCRSet, n1.point);
						Tuple<Shape,Double> t2=GeomUtil.findClosestShapeTo(OCRSet, n2.point);
						
						Point2D pt1=n1.point;
						Point2D pt2=n2.point;
						
						if(t1!=null && t1.v()<ocrTol){
							pt1=GeomUtil.closestPointOnShape(t1.k(), pt2);
						}
						if(t2!=null && t2.v()<ocrTol){
							pt2=GeomUtil.closestPointOnShape(t2.k(), pt1);
						}
						
						
						Edge e= new Edge(i,j,1);
						double score=bm.getLineLikeScore(new Line2D.Double(pt1,pt2));
						//System.out.println("Score:" + score);
						if(score<tol){
							this.edges.add(e);
							econs.accept(Tuple.of(score,e));
						}
					}
				}
			}
		}
		resetCaches();
		return this;
	}

	public ConnectionTable removeOrphanNodes() {
		Map<Integer,List<Edge>> nmap = getEdgeMap();
		
		for(int i =nodes.size()-1;i>=0;i--){
			if(nmap.get(i).isEmpty()){
				removeNode(i);
			}
		}
		return this;
	}
	
	public ConnectionTable removeNodeAndEdges(Node n){
		List<Edge> nedges=n.getEdges();
		this.edges.removeAll(nedges);
		resetCaches();
		return this.removeNode(n.getIndex());
	}
	
	private Map<Integer,List<Edge>> _getEdgeMap(){
		Map<Integer,List<Edge>> nmap = new HashMap<>();
		IntStream.range(0, nodes.size())
		         .forEach(i->{
		        	 nmap.put(i, new ArrayList<>());
		         });
		edges.forEach(e->{
			nmap.computeIfAbsent(e.n1,k->new ArrayList<>()).add(e);
			nmap.computeIfAbsent(e.n2,k->new ArrayList<>()).add(e);
		});
		return nmap;
	}
	public Map<Integer,List<Edge>> getEdgeMap(){
		return _bondMap.get();
	}
	public Map<Node,Integer> getNodeMap(){
		return _nodeMap.get();
	}
	public Map<Node,Integer> _getNodeMap(){
		return IntStream.range(0, nodes.size())
				 .mapToObj(i->i)
		         .collect(Collectors.toMap(i->nodes.get(i), i->i));
	}
	
	

	public ConnectionTable fixBondOrders(List<Shape> likelyOCR, double shortestRealBondRatio, Consumer<Edge> edgeCons) {
		// TODO Auto-generated method stub
		this.edges
		    .stream()
		    .forEach(e->{
		    	Shape s1=GeomUtil.getClosestShapeTo(likelyOCR,e.getPoint1());
		    	Shape s2=GeomUtil.getClosestShapeTo(likelyOCR,e.getPoint2());
		    	if(s1!=s2){
		    		if(s1.contains(e.getPoint1())){
		    			if(s2.contains(e.getPoint2())){
		    				Line2D line = e.getLine();
		    				
		    				Point2D pn1=GeomUtil.getIntersection(s1,line).orElse(null);
		    				Point2D pn2=GeomUtil.getIntersection(s2,line).orElse(null);
		    				if(pn1!=null && pn2!=null){
		    					double realDistance=pn1.distance(pn2);
		    					if(realDistance/e.getBondDistance()<shortestRealBondRatio){
		    						edgeCons.accept(e);
		    					}
		    				}
		    			}
		    		}
		    	}
		    });
		return this;
	}

	public List<Node> getNodes() {
		return this.nodes;
	}
	
	public List<Node> getNodesNotInShapes(List<Shape> shapes, double tol){
		if(shapes.isEmpty())return nodes;
		return nodes.stream()
		     .map(n->Tuple.of(n,GeomUtil.getClosestShapeTo(shapes, n.point)))
		     .filter(t->GeomUtil.distanceTo(t.v(),t.k().point)>tol)
		     .map(t->t.k())
		     .collect(Collectors.toList());
	}

	public ConnectionTable makeMissingNodesForShapes(List<Shape> likelyOCR, double mAX_BOND_TO_AVG_BOND_RATIO_FOR_NOVEL,
			double mIN_BOND_TO_AVG_BOND_RATIO_FOR_NOVEL) {
		double avg=this.getAverageBondLength();
		List<Shape> addShapes=likelyOCR.stream() 
		         .map(oc->Tuple.of(getClosestNodeToShape(oc),oc))
		         .filter(t->t.k().v()>avg*mIN_BOND_TO_AVG_BOND_RATIO_FOR_NOVEL)
				 .filter(t->t.k().v()<avg*mAX_BOND_TO_AVG_BOND_RATIO_FOR_NOVEL)
				 .map(t->t.v())
				 .collect(Collectors.toList());
		
		for(Shape s:addShapes){
			this.addNode(GeomUtil.findCenterOfShape(s));
		}        
		
		return this;
	}

	public ConnectionTable cloneTab() {
		ConnectionTable ctab2 = new ConnectionTable();
		this.nodes.forEach(n->{
			ctab2.addNode(n.point);
			Node nnode=ctab2.nodes.get(ctab2.nodes.size()-1);
			nnode.symbol=n.symbol;
		});
		this.edges.forEach(e->{
			ctab2.addEdge(e.n1, e.n2,e.order);
			Edge nedge=ctab2.edges.get(ctab2.edges.size()-1);
			nedge.setDashed(e.getDashed());
			nedge.setWedge(e.getWedge());
		});
		
		
		return ctab2;
	}

	
	
	
}