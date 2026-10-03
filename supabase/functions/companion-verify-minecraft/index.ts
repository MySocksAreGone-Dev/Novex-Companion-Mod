// Server-side only. No request bodies, headers, tokens or upstream responses are logged.
const json = (status: number, message: unknown) => new Response(JSON.stringify(message), {status, headers: {"Content-Type":"application/json", "Cache-Control":"no-store"}});
Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return json(405,{error:"POST required"});
  const url=Deno.env.get("SUPABASE_URL")!;
  const publicKey=Deno.env.get("SUPABASE_ANON_KEY")!;
  const authorization=request.headers.get("Authorization") || "";
  if (!authorization.startsWith("Bearer ") || authorization.length>17000) return json(401,{error:"Sign in to Novex"});
  try {
    const auth=await fetch(url+"/auth/v1/user",{headers:{apikey:publicKey,Authorization:authorization},signal:AbortSignal.timeout(8000)});
    if(!auth.ok)return json(401,{error:"Novex session expired"});
    const account=await auth.json();
    if(!/^[0-9a-f-]{36}$/i.test(account.id))return json(401,{error:"Invalid Novex identity"});
    const reader=request.body?.getReader();if(!reader)return json(400,{error:"Missing proof"});
    let size=0;const chunks:Uint8Array[]=[];
    while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>20000){await reader.cancel();return json(413,{error:"Request too large"});}chunks.push(value);}
    const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
    const body=JSON.parse(new TextDecoder().decode(bytes));
    const token=body.minecraftAccessToken;
    if(typeof token!=="string" || token.length<20 || token.length>16384 || !/^[A-Za-z0-9._-]+$/.test(token))return json(400,{error:"A signed-in Minecraft session is required"});
    const profileResponse=await fetch("https://api.minecraftservices.com/minecraft/profile",{headers:{Authorization:"Bearer "+token},redirect:"error",signal:AbortSignal.timeout(8000)});
    if(!profileResponse.ok)return json(403,{error:"Minecraft session could not be verified"});
    const profile=await profileResponse.json();
    if(!/^[0-9a-f]{32}$/i.test(profile.id))return json(403,{error:"Minecraft profile unavailable"});
    const id=profile.id.replace(/(.{8})(.{4})(.{4})(.{4})(.{12})/,"$1-$2-$3-$4-$5");
    // This privileged credential exists only in the Supabase server environment.
    const key=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const stored=await fetch(url+"/rest/v1/rpc/companion_record_verified_link",{method:"POST",headers:{apikey:key,Authorization:"Bearer "+key,"Content-Type":"application/json"},body:JSON.stringify({account_id:account.id,minecraft_id:id}),signal:AbortSignal.timeout(8000)});
    if(!stored.ok)return json(503,{error:"Unable to save verification"});
    return json(200,{minecraftId:id});
  }catch{return json(503,{error:"Verification temporarily unavailable"});}
});
