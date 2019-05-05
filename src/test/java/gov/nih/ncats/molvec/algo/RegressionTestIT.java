package gov.nih.ncats.molvec.algo;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

import gov.nih.ncats.chemkit.api.Atom;
import gov.nih.ncats.chemkit.api.AtomCoordinates;
import gov.nih.ncats.chemkit.api.Bond;
import gov.nih.ncats.chemkit.api.Bond.Stereo;
import gov.nih.ncats.chemkit.api.Chemical;
import gov.nih.ncats.chemkit.api.ChemicalBuilder;
import gov.nih.ncats.chemkit.api.inchi.Inchi;
import gov.nih.ncats.molvec.Molvec;
import gov.nih.ncats.molvec.algo.ShellCommandRunner.Monitor;

public class RegressionTestIT {

	public static enum Result{
		CORRECT_FULL_INCHI,
		CORRECT_STEREO_INSENSITIVE_INCHI,
		LARGEST_FRAGMENT_CORRECT_FULL_INCHI,
		LARGEST_FRAGMENT_CORRECT_STEREO_INSENSITIVE_INCHI,
		FORMULA_CORRECT,
		LARGEST_FRAGMENT_FORMULA_CORRECT,

		ATOMS_RIGHT_WRONG_BONDS,
		LARGEST_FRAGMENT_ATOM_RIGHT_WRONG_BONDS,
		ATOM_COUNT_BOND_COUNT_RIGHT_WRONG_LABELS_OR_CONNECTIVITY,
		LARGEST_FRAGMENT_ATOM_COUNT_BOND_COUNT_RIGHT_WRONG_LABELS_OR_CONNECTIVITY,
		WEIRD_SOURCE,
		RIGHT_HEVAY_ATOMS,
		RIGHT_BONDS,
		LARGEST_FRAGMENT_RIGHT_BONDS,
		INCORRECT,
		FOUND_NOTHING,
		ERROR,
		TIMEOUT
	}
	public static class TestResult{
		public Result result;
		public long time;
		
		public static TestResult of(Result r, long ms){
			TestResult tr = new TestResult();
			tr.result=r;
			tr.time=ms;
			return tr;
		}
	}
	

	private File getFile(String fname){
		ClassLoader classLoader = getClass().getClassLoader();
		return new File(classLoader.getResource(fname).getFile());
		
	}
	

	public static Chemical combineChemicals(Chemical c1, Chemical c2){
		ChemicalBuilder nc = c1.copy().toBuilder();
		
		Map<Atom,Atom> oldToNew = new HashMap<>();
		
		for(int i=0;i<c1.getAtomCount();i++){
			Atom aa=nc.atomAt(i);
			AtomCoordinates ac=aa.getAtomCoordinates();
			aa.setAtomCoordinates(AtomCoordinates.valueOf(ac.getX(), ac.getY(), ac.getZ().orElse(0)));
		}
		
		c2.atoms()
		  .forEach(a->{
			  AtomCoordinates ac=a.getAtomCoordinates();
			  
			  Atom na=nc.addAtom(a.getSymbol(), ac.getX(), ac.getY(), ac.getZ().orElse(0));
			  oldToNew.put(a, na);
			  na.setCharge(a.getCharge());
			  na.setMassNumber(a.getMassNumber());
			  na.setAtomCoordinates(AtomCoordinates.valueOf(ac.getX(), ac.getY(), ac.getZ().orElse(0)));
		  });
		
		c2.bonds()
		  .forEach(b->{
			  Atom na1=oldToNew.get(b.getAtom1());
			  Atom na2=oldToNew.get(b.getAtom2());
			  Bond nb=nc.addBond(na1,na2, b.getBondType());
			  if(b.getStereo()!=null && b.getStereo()!=Stereo.NONE){
				  nb.setStereo(b.getStereo());  
			  }
			  //
		  });
//		
		
		return nc.build();
	}
	

	

	public static Chemical getOSRAChemical(File f) throws IOException, InterruptedException{
		StringBuilder resp = new StringBuilder();
		AtomicBoolean done=new AtomicBoolean(false);
		
		Monitor m=(new ShellCommandRunner.Builder()).activeDir("./")
	               .command("osra", "-f sdf", f.getAbsolutePath())
	               .build()
	               .run();
		m.onError(l->{
			try{
				System.err.println(l);
			m.kill();
			}catch(Exception e){
				e.printStackTrace();
			}
		});
		m.onInput(l->{resp.append(l + "\n");});
		m.onKilled(k->{done.set(true);});
	
		while(!done.get()){
			Thread.sleep(10);
		}
		
		StringBuilder sbnew = new StringBuilder();
		
		Chemical fc= Arrays.stream(resp.toString().split("\n"))
		      .map(l->{
		    	  sbnew.append(l+"\n");
		    	  if(l.equals("$$$$")){
		    		  String f1=removeSGroupStuff(sbnew.toString().replace(" *   ", " C   "));
		    		  sbnew.setLength(0);
		    		  try {
						return Chemical.parseMol(f1);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
		    	  }
		    	  return null;
		      })
		      .filter(c->c!=null)
		      .reduce((c1,c2)->combineChemicals(c1,c2))
		      .orElse(new ChemicalBuilder().build());
		
		return fc;
	}
	private static String removeSGroupStuff(String mol){
		AtomicBoolean lastWasAlias = new AtomicBoolean(false);
		Stream<String> stream = Arrays.stream(mol.split("\n"));
		
			return stream
		
				.filter(l->!l.contains("SUP"))
				.filter(l->!l.contains("SGROUP"))
				.filter(l->!l.contains("M  S"))
				.filter(l->{
					if(lastWasAlias.get()){
						lastWasAlias.set(false);
						return false;
					}
					return true;
				})
				.filter(l->{
					if(l.startsWith("A")){
						lastWasAlias.set(true);
						return false;
					}
					lastWasAlias.set(false);
					return true;
				})
				.collect(Collectors.joining("\n"));
		
	}
	private static Chemical getCleanChemical(String mol) throws IOException{
		Chemical nc= Chemical.parseMol(mol);
		
		
		Set<String> metals = Stream.of("Na","K","Li","Mg", "Pt").collect(Collectors.toSet());
		
		Set<String> bondsToRemove = nc.bonds()
		.filter(b->{
			if(metals.contains(b.getAtom1().getSymbol())){
				
				b.getAtom1().setCharge(b.getAtom1().getCharge()+1);
				b.getAtom2().setCharge(b.getAtom2().getCharge()-1);
				return true;
			}else if(metals.contains(b.getAtom2().getSymbol())){
				
				b.getAtom2().setCharge(b.getAtom2().getCharge()+1);
				b.getAtom1().setCharge(b.getAtom1().getCharge()-1);
				return true;
			}
			return false;
		})
		.map(b-> Tuple.of(b.getAtom1(),b.getAtom2()))
		.map(Tuple.vmap(a->a.getAtomIndexInParent()+1))
		.map(Tuple.kmap(a->a.getAtomIndexInParent()+1))
		.map(Tuple.vmap(i->("   " + i)))
		.map(Tuple.kmap(i->("   " + i)))
		.map(Tuple.vmap(i->i.toString().substring(i.toString().length()-3)))
		.map(Tuple.kmap(i->i.toString().substring(i.toString().length()-3)))
		.map(t->t.k()+t.v())
		//.peek(s->System.out.println(s))
		.collect(Collectors.toSet());
		
		if(!bondsToRemove.isEmpty()){
			String[] lines2 = nc.toMol().split("\n");
			//System.out.println("OLD:" + nc.toMol());
			String padBonds = "   " + (nc.getBondCount()-bondsToRemove.size());
			padBonds=padBonds.substring(padBonds.length()-3);
			
			lines2[3]=lines2[3].substring(0, 3) + padBonds + lines2[3].substring(6);
			String mol2=Arrays.stream(lines2)
					          .filter(bl->!bondsToRemove.contains((bl+"      ").substring(0, 6)))
					          .collect(Collectors.joining("\n"));
			
			nc= Chemical.parseMol(mol2);
			//System.out.println("NEW:" + nc.toMol());
		}
		  
		
		return nc;
	}

	public static TestResult testMolecule(File image, File sdf){
		return testMolecule(image, sdf, 60);
	}
	public static TestResult testMolecule(File image, File sdf, long timeoutInSeconds){
		long start= System.currentTimeMillis();
		try{
			AtomicBoolean lastWasAlias = new AtomicBoolean(false);
			String rawMol=null;
			
			boolean assmiles = sdf.getAbsolutePath().endsWith("smi");
			
			try(Stream<String> stream = Files.lines(sdf.toPath())){
				rawMol = stream
			
					.filter(l->!l.contains("SUP"))
					.filter(l->!l.contains("SGROUP"))
					.filter(l->!l.contains("M  S"))
					.filter(l->{
						if(lastWasAlias.get()){
							lastWasAlias.set(false);
							return false;
						}
						return true;
					})
					.filter(l->{
						if(l.startsWith("A")){
							lastWasAlias.set(true);
							return false;
						}
						lastWasAlias.set(false);
						return true;
					})
					.collect(Collectors.joining("\n"));
			}
			Chemical c1=null;
			if(assmiles){
				c1=ChemicalBuilder.createFromSmiles(rawMol).build();
			}else{
				c1=ChemicalBuilder.createFromMol(rawMol,Charset.defaultCharset()).build();
			}
			System.out.println("--------------------------------");
			System.out.println(sdf.getAbsolutePath());
			System.out.println("--------------------------------");
			
			
			
			
//			CachedSupplier<StructureImageExtractor> cget = CachedSupplier.of(()->{
//				try {
//					return new StructureImageExtractor(image);
//				} catch (Exception e1) {
//					throw new RuntimeException(e1);
//				}
//			});


//			Thread th = new Thread(()->{
//				cget.get();
//			});
			
			
			Chemical c;
//			CompletableFuture<String> chemicalCompletableFuture = Molvec.ocrAsync(image);
//
//			try {
//				c = getCleanChemical(chemicalCompletableFuture.get(timeoutInSeconds, TimeUnit.SECONDS));
//			}catch(TimeoutException te) {
//				System.out.println("timeout!!");
//				chemicalCompletableFuture.cancel(true);
//				return TestResult.of(Result.TIMEOUT, System.currentTimeMillis()-start);
//			}catch(Exception e){
//				return TestResult.of(Result.ERROR, System.currentTimeMillis()-start);
//			}
			c = getCleanChemical(getOSRAChemical(image).toMol());
			
//			try{
//				long start = System.currentTimeMillis();
//				th.start();
//				while(true){
//					if(cget.hasRun())break;
//					Thread.sleep(100);
//					if(System.currentTimeMillis()-start > 60000){
//						th.interrupt();
//						System.out.println("OH NO TIMEOUT!\t" + image.getAbsolutePath());
//						return Result.TIMEOUT;
//					}
//				}
//
//
//			}catch(Exception e){
//				return Result.ERROR;
//			}
//
//			StructureImageExtractor sie = cget.get();
//
//			Chemical c=getCleanChemical(sie.getChemical());
//
			
			//c1.makeHydrogensImplicit();
			//c.makeHydrogensImplicit();
			
			long total = System.currentTimeMillis()-start;
			
			if(c.getAtomCount()==0){
				return TestResult.of(Result.FOUND_NOTHING,total);
			}
			String iinchi=Inchi.asStdInchi(c).getKey();
			String rinchi=Inchi.asStdInchi(c1).getKey();
			
			
					
			
			int ratomCount=c1.getAtomCount();
			int iatomCount=c.getAtomCount();
			
			int rbondCount=c1.getBondCount();
			int ibondCount=c.getBondCount();
			
			
			String smilesReal=c1.toSmiles();
			String smilesFound=c.toSmiles();
			
			String formReal=c1.getFormula();
			String formFound=c.getFormula();
			
			System.out.println("Real:" + c1.toSmiles());
			System.out.println("Image:" + c.toSmiles());
			
			if(smilesFound.equals("")){
				return TestResult.of(Result.FOUND_NOTHING,total);
			}
			
			if(rinchi.equals(iinchi)){
				return TestResult.of(Result.CORRECT_FULL_INCHI,total);
			}
			if(rinchi.split("-")[0].equals(iinchi.split("-")[0])){
				return TestResult.of(Result.CORRECT_STEREO_INSENSITIVE_INCHI,total);
			}
			
			if(formReal.equals(formFound)){
				//System.out.println("Matched!");
				return TestResult.of(Result.FORMULA_CORRECT,total);
			}else{
				String withoutHydrogensReal =formReal.replaceAll("H[0-9]*", "");
				String withoutHydrogensFound=formFound.replaceAll("H[0-9]*", "");
				
				if(withoutHydrogensReal.equals(withoutHydrogensFound)){
					return TestResult.of(Result.ATOMS_RIGHT_WRONG_BONDS,total);
				}
				
				if(smilesReal.contains(".") || smilesFound.contains(".") ){
					String largestR=Arrays.stream(smilesReal.split("[.]"))
					      .map(t->Tuple.of(t,t.length()).withVComparator())
					      .max(Comparator.naturalOrder())
					      .map(t->t.k())
					      .orElse(null);
					
					String largestF=Arrays.stream(smilesFound.split("[.]"))
					      .map(t->Tuple.of(t,t.length()).withVComparator())
					      .max(Comparator.naturalOrder())
					      .map(t->t.k())
					      .orElse(null);
					Chemical clargestR=ChemicalBuilder.createFromSmiles(largestR).build();
					Chemical clargestF=ChemicalBuilder.createFromSmiles(largestF).build();

					
					iinchi=Inchi.asStdInchi(clargestF).getKey();
					rinchi=Inchi.asStdInchi(clargestR).getKey();
					
					if(rinchi.equals(iinchi)){
						return TestResult.of(Result.LARGEST_FRAGMENT_CORRECT_FULL_INCHI,total);
					}
					if(rinchi.split("-")[0].equals(iinchi.split("-")[0])){
						return TestResult.of(Result.LARGEST_FRAGMENT_CORRECT_STEREO_INSENSITIVE_INCHI,total);
					}
					
					if(clargestR.getFormula().equals(clargestF.getFormula())){
						return TestResult.of(Result.LARGEST_FRAGMENT_FORMULA_CORRECT,total);
					}
					String fragwHReal =clargestR.getFormula().replaceAll("H[0-9]*", "");
					String fragwHFound=clargestF.getFormula().replaceAll("H[0-9]*", "");
					
					if(fragwHReal.equals(fragwHFound)){
						return TestResult.of(Result.LARGEST_FRAGMENT_ATOM_RIGHT_WRONG_BONDS,total);
					}
					clargestR.makeHydrogensImplicit();
					clargestF.makeHydrogensImplicit();
					
					int fratomCount=clargestR.getAtomCount();
					int fiatomCount=clargestF.getAtomCount();
					
					int frbondCount=clargestR.getBondCount();
					int fibondCount=clargestF.getBondCount();
					if(fratomCount==fiatomCount && frbondCount == fibondCount){
						return TestResult.of(Result.LARGEST_FRAGMENT_ATOM_COUNT_BOND_COUNT_RIGHT_WRONG_LABELS_OR_CONNECTIVITY,total);
					}
				}
				if(smilesReal.contains("|")){
					return TestResult.of(Result.WEIRD_SOURCE,total);
				}
				if(ratomCount==iatomCount && rbondCount == ibondCount){
					return TestResult.of(Result.ATOM_COUNT_BOND_COUNT_RIGHT_WRONG_LABELS_OR_CONNECTIVITY,total);
				}
				
				//System.out.println("NO MATCH!");
				return TestResult.of(Result.INCORRECT,total);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return TestResult.of(Result.ERROR,System.currentTimeMillis()-start);
	}
	
	
//	@Ignore
	@Test
	public void test1(){
		File dir1 = getFile("regressionTest/testSet1");
//		try {
//			ChemicalBuilder cb = ChemicalBuilder.createFromSmiles("CCCC");
//			String ii = Inchi.asStdInchi(cb.build()).getKey();
//			System.out.println(ii);
//			Thread.sleep(2000);
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		List<String> dataMethod = new ArrayList<>();
		dataMethod.add("@Parameterized.Parameters(name=\"{0}\")");
		dataMethod.add("public static List<Object[]> getData(){");
		dataMethod.add("\tFile dir = new File(RegressionTest2.class.getResource(\"/regressionTest/testSet1\").getFile());");

		dataMethod.add("\n\tList<Object[]> list = new ArrayList<>();\n");

			Arrays.stream(dir1.listFiles())
		      .filter(f->f.getName().contains("."))
		      
		      .map(f->Tuple.of(f.getName().split("[.]")[0],f))
		      .collect(Tuple.toGroupedMap())
		      .values()
		      .stream()
		      .filter(l->l.size()==2)
		      
		      .map(l->{
		    	  	if(!l.get(1).getName().toLowerCase().endsWith("tif") && !l.get(1).getName().toLowerCase().endsWith("png")){
		    	  		List<File> flist = new ArrayList<File>();
		    	  		flist.add(l.get(1));
		    	  		flist.add(l.get(0));
		    	  		return flist;
					}
		    	  	return l;
		      })
		      .collect(shuffler(new Random(140l)))		      
		      //.limit(100)

//NOTE, I THINK THIS TECHNICALLY WORKS, BUT SINCE THERE IS PARALLEL THINGS GOING ON IN EACH, IT SOMETIMES WILL STARVE A CASE FOR A LONG TIME
		      .parallel()
		      
		      
		      .map(fl->Tuple.of(fl,testMolecule(fl.get(1),fl.get(0), 400)))
		      .map(t->t.swap())
		      .map(t->Tuple.of(t.k().result,Tuple.of(t,t.v())))
		      .peek(t->System.out.println(t.v().v().get(1).getAbsolutePath() + ":" +t.k()))
		      .collect(Tuple.toGroupedMap())
		      .entrySet()
		      .stream()
//				.sorted()
		      .map(Tuple::of)

		      .forEach(t->{
		    	  Result r=t.k();
		    	  List<List<File>> fl = t.v().stream().map(t1->t1.v()).collect(Collectors.toList());
		    	  
		    	  System.out.println("======================================");
		    	  System.out.println(r.toString() + "\t" + fl.size());
		    	  System.out.println("--------------------------------------");
		    	  System.out.println(t.v().stream().map(tf->tf.v().get(1).getAbsolutePath() + "\t" + tf.k().k().time).collect(Collectors.joining("\n")));

//				  dataMethod.add("\t\tadd"+r.name()+"(list, dir);");
//			
//					System.out.println("\tprivate static void add"+r.name()+"(List<Object[]> list, File dir){\n"+
//						"\t\t//=================================\n"+
//						"\t\t//            " + r.name() + "  " + fl.size() +"\n" +
//						"\t\t//--------------------------------------\n");
//					for(List<File> f : fl) {
//						String fileName = f.get(1).getName();
//						//trim off .png
//						String noExt = fileName.substring(0, fileName.length()-4);
//						System.out.println("\t\tlist.add(test(RegressionTestIT.Result." + r.name()+", dir, \"" + noExt + "\"));");
//					}
//					System.out.println("\t}\n");

		      });

//			dataMethod.add("\t\treturn list;\n\t}");
//			System.out.println(dataMethod.stream().collect(Collectors.joining("\n")));
	}
	
	public static <T> Collector<T,List<T>,Stream<T>> shuffler(Random r){
		
		return new Collector<T,List<T>,Stream<T>>(){

			@Override
			public BiConsumer<List<T>, T> accumulator() {
				return (l,t)->{
					l.add(t);
				};
			}

			@Override
			public Set<java.util.stream.Collector.Characteristics> characteristics() {
				//java.util.stream.Collector.Characteristics.
				return new HashSet<java.util.stream.Collector.Characteristics>();
			}

			@Override
			public BinaryOperator<List<T>> combiner() {
				return (l1,l2)->{
					l1.addAll(l2);
					return l1;
				};
			}

			@Override
			public Function<List<T>, Stream<T>> finisher() {
				return (u)->{
					Collections.shuffle(u,r);
					return u.stream();
				};
			}

			@Override
			public Supplier<List<T>> supplier() {
				return ()-> new ArrayList<T>();
			}

		};
	}
	
	
}
