
describe command('java -version') do
  its(:stdout) { should match /java version "1\.8.*/ }
end

describe command('echo $JAVA_TOOL_OPTIONS') do
  its(:stdout) { should match /-javaagent:\/usr\/local\/log4j-hotpatch\/Log4JHotpatch\.jar/ }
end