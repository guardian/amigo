describe command('java -version') do
  its(:stdout) { should match /java version "25\..*/ }
end
